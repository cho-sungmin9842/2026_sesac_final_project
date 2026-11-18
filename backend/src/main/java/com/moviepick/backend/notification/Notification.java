package com.moviepick.backend.notification;

import com.moviepick.backend.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

// 예매 완료 알림 1건. 예매한 본인에게 1건, 관리자 전원에게 각 1건씩 생성됩니다(NotificationService 참고).
@Entity
@Table(name = "notifications")
@Getter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "booking_id")
    private Long bookingId;

    // 리뷰 신고 알림에만 값이 있습니다 - 관리자가 "보러가기"를 눌렀을 때 신고 상세(리뷰 내용/작성자/
    // 신고 사유/신고자)를 조회하는 키입니다(NotificationService.notifyReviewReported).
    @Column(name = "review_report_id")
    private Long reviewReportId;

    // 리뷰 "도움이 됐어요" 알림에만 값이 있습니다 - 클릭 시 그 리뷰가 있는 영화 상세로 이동하는 키입니다
    // (NotificationService.notifyReviewHelpful). movieId는 movie_title처럼 그대로 복사해둔 값이라,
    // 리뷰가 나중에 지워져 reviewId가 SET NULL되어도 영화 상세로는 계속 이동할 수 있습니다.
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "movie_id", length = 64)
    private String movieId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(User user, String message, Long bookingId) {
        this(user, message, bookingId, null, null, null);
    }

    public Notification(User user, String message, Long bookingId, Long reviewReportId) {
        this(user, message, bookingId, reviewReportId, null, null);
    }

    // 리뷰 "도움이 됐어요" 알림 전용 - NotificationService.notifyReviewHelpful에서만 씁니다.
    public Notification(User user, String message, Long reviewId, String movieId) {
        this(user, message, null, null, reviewId, movieId);
    }

    private Notification(User user, String message, Long bookingId, Long reviewReportId, Long reviewId, String movieId) {
        this.user = user;
        this.message = message;
        this.bookingId = bookingId;
        this.reviewReportId = reviewReportId;
        this.reviewId = reviewId;
        this.movieId = movieId;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        this.read = true;
    }
}
