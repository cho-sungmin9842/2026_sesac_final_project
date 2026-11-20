package com.moviepick.backend.review.dto;

import com.moviepick.backend.review.Review;
import com.moviepick.backend.review.ReviewReport;
import com.moviepick.backend.review.ReviewReportReason;

import java.time.LocalDateTime;

// 관리자 알림의 "보러가기" 다이얼로그용 - 신고된 리뷰 내용/작성자와 신고 사유/신고자를 한 번에 보여줍니다.
// reviewPhotoUrl은 그 리뷰에 첨부된 사진이 있을 때만 값이 들어갑니다(없으면 null - 프론트는 null이면
// 사진 영역 자체를 안 보여줍니다).
public record ReviewReportDetailDto(
        Long id,
        Long reviewId,
        String movieTitle,
        String reviewContent,
        int reviewScore,
        String reviewAuthorNickname,
        String reviewPhotoUrl,
        String reasonLabel,
        String reporterNickname,
        LocalDateTime reportedAt
) {
    public static ReviewReportDetailDto from(ReviewReport report) {
        Review review = report.getReview();
        return new ReviewReportDetailDto(
                report.getId(),
                review.getId(),
                review.getMovieTitle(),
                review.getContent(),
                review.getScore(),
                review.getUser().getNickname(),
                review.getPhotoUrl(),
                ReviewReportReason.fromCode(report.getReason()).label(),
                report.getReporter().getNickname(),
                report.getCreatedAt()
        );
    }
}
