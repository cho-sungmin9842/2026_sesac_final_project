package com.moviepick.backend.review;

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

// 리뷰 1건에 대한 신고 1건 - 신고자(user_id)와 리뷰(review_id) 조합은 유니크라 같은 사람이 같은
// 리뷰를 두 번 신고할 수 없습니다(db/schema.sql의 uq_review_reports_review_reporter).
@Entity
@Table(name = "review_reports")
@Getter
public class ReviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    // ReviewReportReason의 name().
    @Column(nullable = false, length = 50)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReviewReport() {
    }

    public ReviewReport(Review review, User reporter, String reason) {
        this.review = review;
        this.reporter = reporter;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }
}
