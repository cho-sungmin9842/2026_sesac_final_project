package com.moviepick.backend.screening;

import com.moviepick.backend.movie.MovieService;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 매주 월요일 자정, 그 주(월~일) 상영정보를 상영중인 영화 목록으로부터 자동 생성합니다.
 * 요일 x 상영관 순서를 하나의 흐름으로 보고 영화 목록을 전역 라운드로빈으로 배정합니다(칸마다 커서를
 * 리셋하지 않음) - 그래서 영화 한 편이 한 요일에 몰리지 않고 여러 요일에 걸쳐 자연스럽게 퍼지고,
 * 상영중인 영화 전부가 최소 며칠은 상영 기회를 얻습니다. 한 영화는 하루(양쪽 상영관 합산) 최대 4회까지만
 * 상영하고, 같은 상영관 안에서는 시간이 절대 겹치지 않게 배치합니다.
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
    private static final double PRE_BOOKED_RATIO = 0.15;

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

    @Scheduled(cron = "0 0 0 * * MON")
    public void generateWeeklyScreenings() {
        // cron이 정확히 월요일 자정에만 돌긴 하지만, 재실행/수동 트리거 시에도 항상 "이번 주 월요일"을
        // 정확히 앵커링해둡니다(그래야 날짜 계산이 실제 호출 요일에 좌우되지 않습니다).
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        // 이미 이번 주(월~일) 상영정보가 있으면 재생성하지 않습니다.
        if (screeningRepository.existsByDateBetween(monday, sunday)) {
            return;
        }

        // 이 배치 호출 자체가 "오늘(월요일) 첫 조회"가 되어, 상영중 스냅샷이 없으면 여기서 만들어집니다.
        List<MovieSummaryDto> movies = movieService.getOrCreateTodayShowingMovies();
        // 상영 1회조차 하루 운영시간에 안 들어가는(비정상적으로 긴 러닝타임) 영화만 배정 대상에서 뺍니다.
        List<MovieSummaryDto> eligible = movies.stream().filter(this::fitsAtLeastOneShow).toList();
        if (eligible.isEmpty()) {
            return;
        }

        List<Theater> theaters = theaterRepository.findAllByOrderByIdAsc();
        // 요일 x 상영관을 순서대로 훑는 동안 커서를 리셋하지 않고 계속 이어갑니다.
        int[] cursor = {0};
        for (LocalDate date = monday; !date.isAfter(sunday); date = date.plusDays(1)) {
            // 같은 날짜 안에서는 상영관이 여러 개라도 영화별 하루 상영 횟수를 합산해서 셉니다.
            Map<String, Integer> showsToday = new HashMap<>();
            for (Theater theater : theaters) {
                generateForTheaterDay(theater, date, eligible, cursor, showsToday);
            }
        }
    }

    private boolean fitsAtLeastOneShow(MovieSummaryDto movie) {
        int runtime = movie.runtimeMinutes() != null ? movie.runtimeMinutes() : DEFAULT_RUNTIME_MINUTES;
        return runtime + CLEANUP_MINUTES <= TOTAL_OPERATING_MINUTES;
    }

    // 전역 커서 위치부터 영화 목록을 라운드로빈으로 훑으며, 하루 상영 횟수(양쪽 상영관 합산)가 다 찬
    // 영화는 건너뛰고, 그렇지 않으면 이 상영관의 다음 빈 시간에 배정합니다. 커서는 건너뛴 경우에도
    // 전진하므로, 다음 상영관/다음 날짜로 넘어갔을 때 항상 이어서 다른 영화들을 마주치게 됩니다.
    private void generateForTheaterDay(
            Theater theater, LocalDate date, List<MovieSummaryDto> movies, int[] cursor, Map<String, Integer> showsToday
    ) {
        LocalTime currentTime = DAY_START;
        int consecutiveSkips = 0;

        while (consecutiveSkips < movies.size()) {
            MovieSummaryDto movie = movies.get(cursor[0] % movies.size());
            cursor[0]++;

            int shownToday = showsToday.getOrDefault(movie.id(), 0);
            if (shownToday >= MAX_SHOWS_PER_MOVIE_PER_DAY) {
                consecutiveSkips++;
                continue; // 이 영화는 오늘 이미 다 찼으니 다음 영화로(이 상영관의 남은 시간은 아직 유효).
            }

            int runtime = movie.runtimeMinutes() != null ? movie.runtimeMinutes() : DEFAULT_RUNTIME_MINUTES;
            LocalTime endTime = currentTime.plusMinutes(runtime);
            LocalTime nextStart = currentTime.plusMinutes(runtime + CLEANUP_MINUTES);

            // nextStart가 currentTime보다 앞서면(자정을 넘겨 랩어라운드) 하루 운영시간을 넘긴 것입니다.
            if (nextStart.isBefore(currentTime) || nextStart.isAfter(DAY_END)) {
                return; // 상영관의 08:00~23:00 운영시간이 다 찼으므로 이 상영관/날짜는 종료합니다.
            }

            Screening screening = screeningRepository.save(new Screening(movie.id(), theater, date, currentTime, endTime));
            createSeats(screening);

            showsToday.put(movie.id(), shownToday + 1);
            currentTime = nextStart;
            consecutiveSkips = 0;
        }
    }

    private void createSeats(Screening screening) {
        List<Seat> seats = new ArrayList<>(SEAT_ROWS * SEAT_COLS);
        for (int row = 0; row < SEAT_ROWS; row++) {
            String rowLabel = String.valueOf((char) ('A' + row));
            SeatType seatType = row == 0 ? SeatType.WHEELCHAIR : SeatType.NORMAL;
            for (int col = 1; col <= SEAT_COLS; col++) {
                SeatStatus status = ThreadLocalRandom.current().nextDouble() < PRE_BOOKED_RATIO
                        ? SeatStatus.BOOKED
                        : SeatStatus.AVAILABLE;
                seats.add(new Seat(screening.getId(), rowLabel, col, seatType, status));
            }
        }
        seatRepository.saveAll(seats);
    }
}
