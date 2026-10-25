package com.moviepick.backend.booking;

import com.moviepick.backend.booking.dto.BookingTrendPointDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 관리자 "예매/상영관 현황" 화면의 기간별 예매 추이 그래프 전용 - 요청자가 실제로 관리자인지는
// BookingService에서 X-User-Id로 확인합니다.
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/trend")
    public List<BookingTrendPointDto> getDailyTrend(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "14") int days
    ) {
        return bookingService.getDailyTrendForAdmin(userId, days);
    }
}
