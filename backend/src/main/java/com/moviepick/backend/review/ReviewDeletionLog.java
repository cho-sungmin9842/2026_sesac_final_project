package com.moviepick.backend.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

// 관리자 "리뷰 관리" 화면의 삭제 이력 - 리뷰 삭제 당시 값을 그대로 복사해둔 스냅샷입니다. 삭제되는
// 리뷰/작성자/관리자를 FK로 참조하면 그들이 나중에 지워질 때 이 로그까지 같이 사라지므로, 일부러 FK
// 없이 문자열/숫자로만 저장합니다(ReviewService.adminDeleteInternal 참고).
@Entity
@Table(name = "review_deletion_logs")
@Getter
public class ReviewDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "movie_title", nullable = false)
    private String movieTitle;

    @Column(name = "review_author_nickname", nullable = false, length = 100)
    private String reviewAuthorNickname;

    @Column(name = "review_content", nullable = false, columnDefinition = "TEXT")
    private String reviewContent;

    @Column(name = "review_score", nullable = false)
    private Integer reviewScore;

    // 신고로 인한 삭제가 아니면(관리자가 직접 지운 경우) NULL입니다.
    @Column(name = "report_reasons", length = 500)
    private String reportReasons;

    @Column(name = "deleted_by_admin_nickname", nullable = false, length = 100)
    private String deletedByAdminNickname;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    protected ReviewDeletionLog() {
    }

    public ReviewDeletionLog(
            Long reviewId,
            String movieTitle,
            String reviewAuthorNickname,
            String reviewContent,
            Integer reviewScore,
            String reportReasons,
            String deletedByAdminNickname
    ) {
        this.reviewId = reviewId;
        this.movieTitle = movieTitle;
        this.reviewAuthorNickname = reviewAuthorNickname;
        this.reviewContent = reviewContent;
        this.reviewScore = reviewScore;
        this.reportReasons = (reportReasons == null || reportReasons.isBlank()) ? null : reportReasons;
        this.deletedByAdminNickname = deletedByAdminNickname;
        this.deletedAt = LocalDateTime.now();
    }
}
