package com.moviepick.backend.screening;

import com.moviepick.backend.movie.MovieService;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 매주 월요일 자정, 그 주(월~일) 상영정보를 상영중인 영화 목록으로부터 자동 생성합니다.
 * 하루 단위로 완전히 독립적으로 배정하되(요일이 바뀌면 상영관 시간표를 처음부터 다시 채움), 그 날짜
 * 기준 "상영중" 목록을 매일 새로 계산해 주중에 바뀐 상영작도 빠짐없이 반영합니다 - 그래서 상영중인
 * 영화는 전부 매일 최소 1회는 상영하고(상영관 용량이 허락하는 한 최대 4회까지), 같은 영화라도 날짜마다
 * 채우는 순서를 돌려서 상영 시작 시각이 매일 달라집니다. 같은 상영관 안에서는 시간이 절대 겹치지 않게
 * 배치하고, 상영 종료 후 20분의 정리시간을 반드시 비워둡니다.
 */
@Service
public class ScreeningGenerationService {

    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final LocalTime DAY_END = LocalTime.of(23, 0);
    private static final long TOTAL_OPERATING_MINUTES = Duration.between(DAY_START, DAY_END).toMinutes();
    // 한 회차가 끝난 뒤 다음 회차 전까지 정리시간으로 비워두는 시간(분).
    private static final int CLEANUP_MINUTES = 20;
    // 한 영화가 하루에(1관+2관 합산) 상영될 수 있는 최대 횟수.
    private static final int MAX_SHOWS_PER_MOVIE_PER_DAY = 4;
    // KMDB에 러닝타임 정보가 없는 영화에 쓰는 기본값(분).
    private static final int DEFAULT_RUNTIME_MINUTES = 120;
    private static final int SEAT_ROWS = 8; // A~H
    private static final int SEAT_COLS = 14;

    private final MovieService movieService;
    private final TheaterRepository theaterRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;

    public ScreeningGenerationService(
            MovieService movieService,
            TheaterRepository theaterRepository,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository
    ) {
        this.movieService = movieService;
        this.theaterRepository = theaterRepository;
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
    }

    // "매주 월요일 자정"이라는 기준 시각은 아래 cron 하나로 정의해두되, 그 정확한 순간에 서버가 떠
    // 있지 않아도(재시작 중이었거나, 잠깐 멈춰 있었거나) 결국엔 자동으로 생성되도록 두 가지 안전망을
    // 더 둡니다 - generateWeeklyScreenings 자체가 "이번 주 분량이 이미 있으면 바로 리턴"하는 멱등
    // 구조라, 아래 셋 중 뭐가 먼저 실행되든 안전하게 한 번만 실제로 생성됩니다.
    // 1) 서버가 시작되는 시점에 한 번 확인(재시작 직후에도 바로 반영).
    @EventListener(ApplicationReadyEvent.class)
    public void generateOnStartupIfMissing() {
        generateWeeklyScreenings();
    }

    // 2) 서버가 계속 떠 있는 동안에도 한 시간마다 한 번씩 확인 - 자정 그 순간에 잠깐 응답이 안 되는
    // 상태였거나 cron 자체가 어떤 이유로 못 돌았어도, 길어야 한 시간 안에는 자동으로 따라잡습니다.
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void generateHourlyIfMissing() {
        generateWeeklyScreenings();
    }

    // 3) 실제 기준 시각 - 매주 월요일 정각. 서버가 그 순간에 떠 있었다면 이걸로 바로 생성되고, 위 두
    // 안전망은 그냥 조용히 넘어갑니다.
    //
    // synchronized로 막아두는 이유: 위 세 트리거(시작 시점/매시간/월요일 cron)는 서로 다른 스레드에서
    // 돌 수 있어, 동시에 들어오면 "이번 주 분량 있는지 확인" → "없으면 생성"이 원자적이지 않아 두 스레드가
    // 동시에 통과해 같은 상영관/시간대에 중복으로 상영정보를 만들 수 있습니다(겹치는 상영 시간 버그의 원인).
    // 이 메서드는 실행 빈도가 낮고(주 1회 수준) 내부에서 오래 걸리는 외부 I/O(KMDB)가 있어도 다른 요청을
    // 막지 않으므로, 메서드 전체를 잠가도 안전합니다.
    @Scheduled(cron = "0 0 0 * * MON")
    public synchronized void generateWeeklyScreenings() {
        // cron이 정확히 월요일 자정에만 돌긴 하지만, 재실행/수동 트리거 시에도 항상 "이번 주 월요일"을
        // 정확히 앵커링해둡니다(그래야 날짜 계산이 실제 호출 요일에 좌우되지 않습니다).
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        // 이미 이번 주(월~일) 상영정보가 있으면 재생성하지 않습니다.
        if (screeningRepository.existsByDateBetween(monday, sunday)) {
            return;
        }

        List<Theater> theaters = theaterRepository.findAllByOrderByIdAsc();
        // 이번 주 들어 영화별로 지금까지 몇 회 상영했는지 누적합니다 - 상영관 용량이 모자라 그날 상영중인
        // 영화를 전부 다 돌리지 못하는 날에도, 이 누적치가 적은(아직 이번 주에 한 번도 못 돈) 영화를 항상
        // 최우선으로 배정해 한 주 전체로 보면 결국 전부 최소 1회는 상영되도록 보장합니다.
        Map<String, Integer> weeklyShowCount = new HashMap<>();
        int dayIndex = 0;
        for (LocalDate date = monday; !date.isAfter(sunday); date = date.plusDays(1), dayIndex++) {
            // 상영중 목록을 한 번만 받아 7일 내내 그대로 쓰면, 주중에 새로 개봉했거나(4주 지나) 상영
            // 종료된 영화가 그 날짜 기준 실제 "상영중" 목록과 어긋나 한 주 내내 상영정보가 아예 없는
            // 영화가 생깁니다. 그래서 하루하루 그 날짜 기준으로 상영중 목록을 따로 계산합니다.
            List<MovieSummaryDto> movies = movieService.getOrCreateShowingMoviesForDate(date);
            // 상영 1회조차 하루 운영시간에 안 들어가는(비정상적으로 긴 러닝타임) 영화만 배정 대상에서 뺍니다.
            List<MovieSummaryDto> eligible = movies.stream().filter(this::fitsAtLeastOneShow).toList();
            generateForDay(theaters, date, eligible, dayIndex, weeklyShowCount);
        }
    }

    private boolean fitsAtLeastOneShow(MovieSummaryDto movie) {
        int runtime = movie.runtimeMinutes() != null ? movie.runtimeMinutes() : DEFAULT_RUNTIME_MINUTES;
        return runtime + CLEANUP_MINUTES <= TOTAL_OPERATING_MINUTES;
    }

    // 하루치 상영관 시간표를 처음부터 채웁니다(요일별로 독립적 - 전날 상태를 이어받지 않음).
    // 1단계: 이번 주 상영 횟수가 적은 영화부터 순서대로 최소 1회씩 배정해, 상영관 용량이 모자라 그날
    //       전부를 다 돌리지 못하더라도 한 주 전체로 보면 반드시 전부 최소 1회는 상영되도록 합니다.
    // 2단계: 남는 시간에 한해 같은 순서로 추가 배정하되, 영화당 하루 최대 MAX_SHOWS_PER_MOVIE_PER_DAY회까지만 채웁니다.
    private void generateForDay(
            List<Theater> theaters, LocalDate date, List<MovieSummaryDto> movies, int dayIndex,
            Map<String, Integer> weeklyShowCount
    ) {
        if (theaters.isEmpty() || movies.isEmpty()) {
            return;
        }

        // 매일 똑같은 순서로 채우면 리스트 앞쪽 영화는 항상 상영관이 비어있는 이른 시간에, 뒤쪽 영화는
        // 항상 늦은 시간에 배정되어 "같은 영화가 매일 같은 시작 시각"에 상영되는 문제가 생깁니다. 요일마다
        // 채우는 순서 자체를 돌려서(회전) 동률일 때의 우선순위를 날마다 다르게 깨고, 그 위에 이번 주 상영
        // 횟수가 적은 영화를 우선하는 정렬을 더합니다(용량이 모자란 날엔 아직 한 번도 못 돈 영화가 항상
        // 먼저 배정되어야 하므로). 결과적으로 같은 영화도 날짜가 바뀌면 그날 몇 번째로 배정되는지가 달라져
        // 상영 시작 시각도 날마다 달라집니다.
        List<MovieSummaryDto> rotated = new ArrayList<>(movies);
        Collections.rotate(rotated, dayIndex);
        rotated.sort(Comparator.comparingInt(movie -> weeklyShowCount.getOrDefault(movie.id(), 0)));

        // 위 회전/정렬로 "어느 영화가 먼저 배정되는지"는 매일 달라지지만, 각 상영관의 첫 회차는 항상
        // 고정된 DAY_START(08:00)에서 시작하므로 "그날 1번째로 배정된 영화"는 요일이 달라도 똑같이
        // 08:00를 받습니다. 그래서 그 영화가 여러 날 연속으로 1번째를 차지하면 시작 시각이 겹칩니다.
        // 하루의 시작 기준 시각 자체를 요일마다 조금씩 밀어서(최대 42분) 이 경우에도 시작 시각이 달라지게 합니다.
        LocalTime dayStart = DAY_START.plusMinutes((dayIndex * 7) % 60);

        Map<Long, LocalTime> theaterCursor = new HashMap<>();
        for (Theater theater : theaters) {
            theaterCursor.put(theater.getId(), dayStart);
        }
        Map<String, Integer> showsToday = new HashMap<>();
        // 날마다 어느 상영관부터 채우기 시작할지도 함께 돌려가며, 특정 상영관에만 영화가 쏠리지 않게 합니다.
        int theaterStart = dayIndex % theaters.size();

        for (MovieSummaryDto movie : rotated) {
            if (placeOneShow(theaters, theaterCursor, showsToday, movie, date, theaterStart)) {
                weeklyShowCount.merge(movie.id(), 1, Integer::sum);
            }
        }

        int cursor = 0;
        int consecutiveSkips = 0;
        while (consecutiveSkips < rotated.size()) {
            MovieSummaryDto movie = rotated.get(cursor % rotated.size());
            cursor++;

            int shownToday = showsToday.getOrDefault(movie.id(), 0);
            if (shownToday >= MAX_SHOWS_PER_MOVIE_PER_DAY) {
                consecutiveSkips++;
                continue; // 이 영화는 오늘 이미 다 찼으니 다음 영화로.
            }

            boolean placed = placeOneShow(theaters, theaterCursor, showsToday, movie, date, theaterStart);
            if (placed) {
                weeklyShowCount.merge(movie.id(), 1, Integer::sum);
            }
            consecutiveSkips = placed ? 0 : consecutiveSkips + 1;
            // 어느 영화도 더 못 들어갈 만큼 모든 상영관이 꽉 찼으면 consecutiveSkips가 movies.size()에 도달해 종료합니다.
        }
    }

    // theaterStart부터 상영관을 순서대로 훑어 이 영화가 들어갈 수 있는 첫 빈 시간에 배정합니다.
    // 배정에 성공하면 true, 모든 상영관에 더 이상 자리가 없으면 false를 돌려줍니다.
    private boolean placeOneShow(
            List<Theater> theaters, Map<Long, LocalTime> theaterCursor, Map<String, Integer> showsToday,
            MovieSummaryDto movie, LocalDate date, int theaterStart
    ) {
        int runtime = movie.runtimeMinutes() != null ? movie.runtimeMinutes() : DEFAULT_RUNTIME_MINUTES;

        for (int i = 0; i < theaters.size(); i++) {
            Theater theater = theaters.get((theaterStart + i) % theaters.size());
            LocalTime currentTime = theaterCursor.get(theater.getId());

            LocalTime endTime = currentTime.plusMinutes(runtime);
            LocalTime nextStart = currentTime.plusMinutes(runtime + CLEANUP_MINUTES);

            // nextStart가 currentTime보다 앞서면(자정을 넘겨 랩어라운드) 하루 운영시간을 넘긴 것이라,
            // 이 상영관은 오늘 더 못 들어가니 다음 상영관을 시도합니다.
            if (nextStart.isBefore(currentTime) || nextStart.isAfter(DAY_END)) {
                continue;
            }

            Screening screening = screeningRepository.save(new Screening(movie.id(), theater, date, currentTime, endTime));
            createSeats(screening);

            theaterCursor.put(theater.getId(), nextStart);
            showsToday.merge(movie.id(), 1, Integer::sum);
            return true;
        }
        return false;
    }

    // 새로 생성하는 상영정보는 전부 빈 좌석(AVAILABLE)으로 시작합니다 - 사용자가 실제로 예매해야 BOOKED로
    // 바뀌어야 하므로, 미리 임의로 일부를 BOOKED로 채워두지 않습니다.
    private void createSeats(Screening screening) {
        List<Seat> seats = new ArrayList<>(SEAT_ROWS * SEAT_COLS);
        for (int row = 0; row < SEAT_ROWS; row++) {
            String rowLabel = String.valueOf((char) ('A' + row));
            SeatType seatType = row == 0 ? SeatType.WHEELCHAIR : SeatType.NORMAL;
            for (int col = 1; col <= SEAT_COLS; col++) {
                seats.add(new Seat(screening.getId(), rowLabel, col, seatType, SeatStatus.AVAILABLE));
            }
        }
        seatRepository.saveAll(seats);
    }
}
