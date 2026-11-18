package com.moviepick.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHelpfulNotificationRepository extends JpaRepository<ReviewHelpfulNotification, Long> {

    // ReviewService.toggleHelpful - 이 사람에게 이 리뷰로 이미 "도움이 됐어요" 알림을 보낸 적이 있는지 확인합니다.
    boolean existsByReviewIdAndVoterUserId(Long reviewId, Long voterUserId);
}
