package com.moviepick.backend.booking;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import com.moviepick.backend.booking.dto.ReservedSeatsDto;
import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class BookingService {

    // 목업 화면 그대로 좌석당 가격을 고정값으로 둡니다(상영관/시간대별 가격 정책은 범위 밖).
    private static final int PRICE_PER_SEAT = 12000;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    public ReservedSeatsDto getReservedSeats(String movieId, String theater, LocalDate showDate, String showtime) {
        Set<String> seats = new TreeSet<>();
        for (Booking booking : findBookings(movieId, theater, showDate, showtime)) {
            seats.addAll(booking.getSeats());
        }
        return new ReservedSeatsDto(List.copyOf(seats));
    }

    public BookingDto create(Long userId, BookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        Set<String> alreadyReserved = new LinkedHashSet<>();
        for (Booking booking : findBookings(request.movieId(), request.theater(), request.showDate(), request.showtime())) {
            alreadyReserved.addAll(booking.getSeats());
        }

        Set<String> requestedSeats = new LinkedHashSet<>(request.seats());
        Set<String> conflicting = new LinkedHashSet<>(requestedSeats);
        conflicting.retainAll(alreadyReserved);
        if (!conflicting.isEmpty()) {
            throw new ApiException("이미 예약된 좌석입니다: " + String.join(", ", conflicting), HttpStatus.CONFLICT);
        }

        Booking booking = new Booking(
                user,
                request.movieId(),
                request.movieTitle(),
                request.theater(),
                request.showDate(),
                request.showtime(),
                requestedSeats,
                requestedSeats.size() * PRICE_PER_SEAT
        );
        return BookingDto.from(bookingRepository.save(booking));
    }

    private List<Booking> findBookings(String movieId, String theater, LocalDate showDate, String showtime) {
        return bookingRepository.findByMovieIdAndTheaterAndShowDateAndShowtime(movieId, theater, showDate, showtime);
    }
}
