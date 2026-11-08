package com.moviepick.backend.booking;

import com.moviepick.backend.booking.dto.AdminSeatDto;
import com.moviepick.backend.booking.dto.BookingTrendPointDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<BookingTrendPointDto> getDailyTrend(@RequestHeader("X-User-Id") Long userId) {
        return bookingService.getDailyTrendForAdmin(userId);
    }

    // 관리자 "예매/상영관 현황" 좌석 배치도 - 예매 완료 좌석마다 예매자 닉네임을 함께 내려줍니다.
    @GetMapping("/screenings/{screeningId}/seats")
    public List<AdminSeatDto> getSeatsForAdmin(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long screeningId
    ) {
        return bookingService.getSeatsForAdmin(userId, screeningId);
    }

    // 관리자 "예매/상영관 현황" 좌석 배치도 - 예매 완료 좌석을 클릭했을 때 그 자리에서 바로 취소합니다.
    @DeleteMapping("/{bookingId}")
    public void cancelForAdmin(@RequestHeader("X-User-Id") Long userId, @PathVariable Long bookingId) {
        bookingService.cancelForAdmin(userId, bookingId);
    }
}
