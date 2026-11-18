package com.moviepick.backend.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

// "도움이 됐어요" 알림을 같은 사람(voter)에게 같은 리뷰로 평생 최초 1회만 보내기 위한 이력입니다.
// review_helpful_votes는 추천을 취소하면 행이 지워지지만(토글), 이 테이블은 한 번 쌓이면 절대 지우지
// 않습니다 - 설정→해제→설정을 반복해도 ReviewService.toggleHelpful이 알림을 다시 보내지 않도록
// 막는 용도입니다(2026-11-17: 반복 토글 시 중복 알림이 가던 문제 수정).
@Entity
@Table(name = "review_helpful_notifications")
@Getter
public class ReviewHelpfulNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "voter_user_id", nullable = false)
    private Long voterUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReviewHelpfulNotification() {
    }

    public ReviewHelpfulNotification(Long reviewId, Long voterUserId) {
        this.reviewId = reviewId;
        this.voterUserId = voterUserId;
        this.createdAt = LocalDateTime.now();
    }
}
