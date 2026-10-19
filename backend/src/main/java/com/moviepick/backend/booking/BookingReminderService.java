package com.moviepick.backend.booking;

import com.moviepick.backend.movie.Movie;
import com.moviepick.backend.movie.MovieRepository;
import com.moviepick.backend.notification.NotificationService;
import com.moviepick.backend.screening.Screening;
import com.moviepick.backend.screening.ScreeningRepository;
import com.moviepick.backend.screening.Seat;
import com.moviepick.backend.screening.SeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 예매한 영화의 상영이 임박하면 예매한 본인에게 알림을 보냅니다(2026-10-19 요청).
 * <ul>
 *   <li><b>상영 당일</b>: 상영일 아침 {@link #DAY_OF_REMINDER_TIME}에 "오늘 상영하는 예매가 있다"고 알립니다.</li>
 *   <li><b>상영 1시간 전</b>: 시작 {@link #HOURS_BEFORE_SHOWING}시간 전이 되면 알립니다(처음엔 3시간 전이었으나
 *       2026-10-19에 1시간 전으로 바꿨습니다). 아직 시작 안 한 예매라면 언제 예매했든 이 알림은 반드시 한 번
 *       나갑니다(예매가 시작 30분 전까지 가능해서, 시작 1시간 이내에 예매한 건도 곧바로 한 번 받습니다).</li>
 * </ul>
 * 알림은 기존 알림(NotificationService)으로 쌓이므로 프론트 종 아이콘이 20초마다 폴링해서 그대로 보여줍니다.
 * 같은 예매에 같은 종류를 두 번 보내지 않도록, 이미 보낸 알림 문구의 앞머리(Reminder.prefix)로 중복을 확인합니다
 * (별도 컬럼을 추가하지 않아 DB 스키마 변경이 필요 없습니다).
 * <p>
 * 주의: 이 중복 확인은 "확인 후 저장"이라 원자적이지 않습니다. 백엔드를 여러 개(replicas &gt; 1) 띄우면 같은 순간에
 * 두 인스턴스가 모두 "아직 안 보냄"으로 판단해 알림이 중복될 수 있습니다(다른 스케줄 배치와 같은 한계).
 */
@Slf4j
@Service
public class BookingReminderService {

    // 상영 당일 알림을 보내는 시각(상영일 아침).
    static final LocalTime DAY_OF_REMINDER_TIME = LocalTime.of(8, 0);
    // 상영 시작 몇 시간 전에 알릴지.
    static final int HOURS_BEFORE_SHOWING = 1;

    enum Reminder {
        DAY_OF("[상영 당일]", "오늘 상영하는 예매 내역이 있습니다"),
        ONE_HOUR_BEFORE("[상영 " + HOURS_BEFORE_SHOWING + "시간 전]", "곧 상영이 시작됩니다");

        final String prefix;
        final String text;

        Reminder(String prefix, String text) {
            this.prefix = prefix;
            this.text = text;
        }

        String message(String summary) {
            return prefix + " " + text + " - " + summary;
        }
    }

    private final BookingRepository bookingRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final NotificationService notificationService;

    public BookingReminderService(
            BookingRepository bookingRepository,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            MovieRepository movieRepository,
            NotificationService notificationService
    ) {
        this.bookingRepository = bookingRepository;
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
        this.notificationService = notificationService;
    }

    // 1분마다 오늘~내일 상영분만 훑습니다(새벽 상영의 3시간 전은 전날 밤일 수 있어서 내일까지 봅니다). 서버를 잠깐
    // 껐다 켜도 다음 실행에서 아직 안 보낸 알림이 자연스럽게 이어서 나갑니다(이미 시작한 상영은 보내지 않습니다).
    @Scheduled(fixedRate = 60 * 1000)
    @Transactional
    public void sendShowingReminders() {
        sendShowingReminders(LocalDateTime.now());
    }

    // 시각을 인자로 받는 본체 - 테스트에서 "지금"을 고정해 호출하려고 분리했습니다.
    void sendShowingReminders(LocalDateTime now) {
        LocalDate today = now.toLocalDate();

        List<Booking> bookings = bookingRepository.findByScreeningDateBetween(today, today.plusDays(1));
        if (bookings.isEmpty()) {
            return;
        }

        List<Long> screeningIds = bookings.stream().map(Booking::getScreeningId).distinct().toList();
        Map<Long, Screening> screeningsById = screeningRepository.findAllById(screeningIds).stream()
                .collect(Collectors.toMap(Screening::getId, s -> s));

        int sent = 0;
        for (Booking booking : bookings) {
            Screening screening = screeningsById.get(booking.getScreeningId());
            if (screening == null) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(screening.getDate(), screening.getStartTime());
            Reminder reminder = dueReminder(booking.getCreatedAt(), start, now);
            if (reminder == null) {
                continue;
            }
            Long userId = booking.getUser().getId();
            if (notificationService.alreadyNotified(userId, booking.getId(), reminder.prefix)) {
                continue;
            }

            notificationService.notifyShowingReminder(
                    booking.getUser(), booking.getId(), reminder.message(summary(booking, screening, start)));
            sent++;
        }
        if (sent > 0) {
            log.info("상영 임박 알림 {}건을 보냈습니다.", sent);
        }
    }

    /**
     * 지금 이 예매에 보내야 할 알림 종류를 정합니다(보낼 게 없으면 null). 이미 보냈는지는 여기서 보지 않습니다.
     * <ul>
     *   <li>이미 시작한 상영 → 없음</li>
     *   <li>시작 1시간 이내 → "1시간 전" 알림. 언제 예매했든 보냅니다(시작 1시간 이내에 막 예매한 건도 한 번은 받게
     *       해서, 아직 시작 안 한 예매는 임박 알림을 최소 1번 받도록 보장합니다). 이 구간에서는 당일 알림이 1시간 전
     *       알림과 겹치므로 따로 보내지 않습니다.</li>
     *   <li>그 전이면서 상영일 아침(08:00) 이후 → "당일" 알림. 단, 아침 8시 이후에 예매한 건은 사용자가 방금 예매한
     *       것이라 보내지 않습니다(그 건도 1시간 전 알림은 따로 받습니다).</li>
     * </ul>
     */
    static Reminder dueReminder(LocalDateTime bookedAt, LocalDateTime showStart, LocalDateTime now) {
        if (!showStart.isAfter(now)) {
            return null;
        }
        LocalDateTime oneHourBefore = showStart.minusHours(HOURS_BEFORE_SHOWING);
        if (!now.isBefore(oneHourBefore)) {
            return Reminder.ONE_HOUR_BEFORE;
        }
        LocalDateTime dayOfTime = LocalDateTime.of(showStart.toLocalDate(), DAY_OF_REMINDER_TIME);
        if (!now.isBefore(dayOfTime) && bookedAt.isBefore(dayOfTime)) {
            return Reminder.DAY_OF;
        }
        return null;
    }

    // 예매 완료/변경 알림과 같은 형식의 요약: "영화제목" 상영관 날짜 시간 · 좌석 A1, A2
    private String summary(Booking booking, Screening screening, LocalDateTime start) {
        Movie movie = movieRepository.findById(screening.getMovieId()).orElse(null);
        String title = movie != null ? movie.getTitle() : screening.getMovieId();
        List<Seat> seats = seatRepository.findAllById(
                booking.getSeats().stream().map(BookingSeat::getSeatId).toList());
        String seatLabels = seats.stream()
                .filter(Objects::nonNull)
                .map(seat -> seat.getRowLabel() + seat.getColNo())
                .collect(Collectors.joining(", "));
        return "\"" + title + "\" " + screening.getTheater().getName() + " " + start.toLocalDate() + " "
                + screening.getStartTime() + " · 좌석 " + seatLabels;
    }
}
