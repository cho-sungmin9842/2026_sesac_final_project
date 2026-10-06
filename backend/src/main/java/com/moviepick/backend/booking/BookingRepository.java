package com.moviepick.backend.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    // 마이페이지 "예매 내역" 탭 - 최신 예매부터 보여줍니다.
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 마이페이지 좌석 배치도 다이얼로그 - 이 상영의 좌석별로 누가 예매했는지(user_id) 알아내는 데 씁니다.
    List<Booking> findByScreeningId(Long screeningId);

    // 관리자 "회원 관리" 화면 - 회원별 예매 건수.
    long countByUserId(Long userId);
}
