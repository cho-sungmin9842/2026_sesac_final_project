package com.moviepick.backend.booking;

import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import com.moviepick.backend.booking.dto.ReservedSeatsDto;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/reserved-seats")
    public ReservedSeatsDto reservedSeats(
            @RequestParam String movieId,
            @RequestParam String theater,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate showDate,
            @RequestParam String showtime
    ) {
        return bookingService.getReservedSeats(movieId, theater, showDate, showtime);
    }

    @PostMapping
    public BookingDto create(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody BookingRequest request) {
        return bookingService.create(userId, request);
    }
}
