package com.moviepick.backend.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    // 마이페이지 "예매 내역" 탭 - 최신 예매부터 보여줍니다.
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
}
