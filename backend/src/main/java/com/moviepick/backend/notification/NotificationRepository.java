package com.moviepick.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    // 상영 임박 알림이 같은 예매에 같은 종류로 두 번 나가지 않게 하는 중복 확인용입니다(문구 앞머리 [상영 당일] 등으로 구분).
    boolean existsByUserIdAndBookingIdAndMessageStartingWith(Long userId, Long bookingId, String messagePrefix);
}
