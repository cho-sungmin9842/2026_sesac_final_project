package com.moviepick.backend.booking;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.booking.BookingReminderService.Reminder;
import com.moviepick.backend.movie.Movie;
import com.moviepick.backend.movie.MovieRepository;
import com.moviepick.backend.notification.NotificationService;
import com.moviepick.backend.screening.Screening;
import com.moviepick.backend.screening.ScreeningRepository;
import com.moviepick.backend.screening.Seat;
import com.moviepick.backend.screening.SeatRepository;
import com.moviepick.backend.screening.Theater;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingReminderServiceTest {

    private static final LocalDate SHOW_DATE = LocalDate.of(2026, 10, 21);

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 10, day, hour, minute);
    }

    // ───────── 어떤 알림을 보낼지 판단하는 규칙 ─────────

    @Test
    void 상영일_아침_8시부터_당일_알림_그_전에는_없음() {
        LocalDateTime showStart = at(21, 18, 49);
        LocalDateTime bookedEarlier = at(15, 10, 0);

        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(20, 23, 59))).isNull();
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 7, 59))).isNull();
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 8, 0))).isEqualTo(Reminder.DAY_OF);
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 17, 48))).isEqualTo(Reminder.DAY_OF);
    }

    @Test
    void 상영_1시간_전부터_1시간전_알림_시작한_뒤에는_없음() {
        LocalDateTime showStart = at(21, 18, 49);
        LocalDateTime bookedEarlier = at(15, 10, 0);

        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 17, 49)))
                .isEqualTo(Reminder.ONE_HOUR_BEFORE);
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 18, 48)))
                .isEqualTo(Reminder.ONE_HOUR_BEFORE);
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 18, 49))).isNull();
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 20, 0))).isNull();
    }

    @Test
    void 아침_일찍_상영이면_1시간전_알림이_당일_알림을_대신한다() {
        LocalDateTime showStart = at(21, 9, 0);
        LocalDateTime bookedEarlier = at(15, 10, 0);

        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 7, 59))).isNull();
        // 1시간 전(08:00)과 당일 알림 시각(08:00)이 겹치면 1시간 전 알림 하나만 나갑니다.
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 8, 0)))
                .isEqualTo(Reminder.ONE_HOUR_BEFORE);
    }

    @Test
    void 상영이_9시_30분이면_8시에_당일_알림_8시30분에_1시간전_알림() {
        LocalDateTime showStart = at(21, 9, 30);
        LocalDateTime bookedEarlier = at(15, 10, 0);

        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 8, 0))).isEqualTo(Reminder.DAY_OF);
        assertThat(BookingReminderService.dueReminder(bookedEarlier, showStart, at(21, 8, 30)))
                .isEqualTo(Reminder.ONE_HOUR_BEFORE);
    }

    @Test
    void 당일_아침_8시_이후에_예매했으면_당일_알림은_없고_1시간전_알림만() {
        LocalDateTime showStart = at(21, 18, 49);
        LocalDateTime bookedSameDayMorning = at(21, 9, 0);

        assertThat(BookingReminderService.dueReminder(bookedSameDayMorning, showStart, at(21, 10, 0))).isNull();
        assertThat(BookingReminderService.dueReminder(bookedSameDayMorning, showStart, at(21, 17, 49)))
                .isEqualTo(Reminder.ONE_HOUR_BEFORE);
    }

    @Test
    void 상영_1시간_안에_예매해도_1시간전_알림은_한_번_받는다() {
        LocalDateTime showStart = at(21, 18, 49);
        LocalDateTime bookedJustNow = at(21, 18, 10);

        assertThat(BookingReminderService.dueReminder(bookedJustNow, showStart, at(21, 18, 11)))
                .isEqualTo(Reminder.ONE_HOUR_BEFORE);
    }

    // ───────── 배치 전체 흐름(저장소는 가짜) ─────────

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final ScreeningRepository screeningRepository = mock(ScreeningRepository.class);
    private final SeatRepository seatRepository = mock(SeatRepository.class);
    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final BookingReminderService service = new BookingReminderService(
            bookingRepository, screeningRepository, seatRepository, movieRepository, notificationService);

    private Booking stubBooking(User user, LocalDateTime createdAt) {
        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(100L);
        when(booking.getUser()).thenReturn(user);
        when(booking.getScreeningId()).thenReturn(7L);
        when(booking.getCreatedAt()).thenReturn(createdAt);
        when(booking.getSeats()).thenReturn(List.of(new BookingSeat(11L, "adult")));
        when(bookingRepository.findByScreeningDateBetween(any(), any())).thenReturn(List.of(booking));

        Theater theater = mock(Theater.class);
        when(theater.getName()).thenReturn("1관");
        Screening screening = mock(Screening.class);
        when(screening.getId()).thenReturn(7L);
        when(screening.getMovieId()).thenReturn("K_1");
        when(screening.getTheater()).thenReturn(theater);
        when(screening.getDate()).thenReturn(SHOW_DATE);
        when(screening.getStartTime()).thenReturn(LocalTime.of(18, 49));
        when(screeningRepository.findAllById(any())).thenReturn(List.of(screening));

        Movie movie = mock(Movie.class);
        when(movie.getTitle()).thenReturn("가족여행");
        when(movieRepository.findById("K_1")).thenReturn(Optional.of(movie));

        Seat seat = mock(Seat.class);
        when(seat.getRowLabel()).thenReturn("D");
        when(seat.getColNo()).thenReturn(3);
        when(seatRepository.findAllById(any())).thenReturn(List.of(seat));
        return booking;
    }

    @Test
    void 알림_대상이면_예매한_본인에게_요약이_담긴_알림을_저장한다() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(54L);
        stubBooking(user, at(15, 10, 0));
        when(notificationService.alreadyNotified(anyLong(), anyLong(), anyString())).thenReturn(false);

        service.sendShowingReminders(at(21, 17, 49));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyShowingReminder(eq(user), eq(100L), message.capture());
        assertThat(message.getValue())
                .startsWith("[상영 1시간 전]")
                .contains("\"가족여행\"", "1관", "2026-10-21", "18:49", "좌석 D3");
    }

    @Test
    void 이미_보낸_알림이면_다시_보내지_않는다() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(54L);
        stubBooking(user, at(15, 10, 0));
        when(notificationService.alreadyNotified(54L, 100L, "[상영 1시간 전]")).thenReturn(true);

        service.sendShowingReminders(at(21, 17, 50));

        verify(notificationService, never()).notifyShowingReminder(any(), anyLong(), anyString());
    }

    @Test
    void 아직_알릴_때가_아니면_아무것도_보내지_않는다() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(54L);
        stubBooking(user, at(15, 10, 0));

        service.sendShowingReminders(at(20, 12, 0));

        verify(notificationService, never()).notifyShowingReminder(any(), anyLong(), anyString());
    }
}
