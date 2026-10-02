package com.moviepick.backend.booking;

import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * 마이페이지 "예매 내역" 탭 - 최신 예매부터 내려줍니다.
     */
    @GetMapping
    public List<BookingDto> list(@RequestHeader("X-User-Id") Long userId) {
        return bookingService.listByUser(userId);
    }

    @PostMapping
    public BookingDto create(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody BookingRequest request) {
        return bookingService.create(userId, request);
    }
}
