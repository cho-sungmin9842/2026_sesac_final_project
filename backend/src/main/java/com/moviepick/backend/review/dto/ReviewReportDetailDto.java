package com.moviepick.backend.review.dto;

import com.moviepick.backend.review.Review;
import com.moviepick.backend.review.ReviewReport;
import com.moviepick.backend.review.ReviewReportReason;

import java.time.LocalDateTime;

// 관리자 알림의 "보러가기" 다이얼로그용 - 신고된 리뷰 내용/작성자와 신고 사유/신고자를 한 번에 보여줍니다.
public record ReviewReportDetailDto(
        Long id,
        String movieTitle,
        String reviewContent,
        int reviewScore,
        String reviewAuthorNickname,
        String reasonLabel,
        String reporterNickname,
        LocalDateTime reportedAt
) {
    public static ReviewReportDetailDto from(ReviewReport report) {
        Review review = report.getReview();
        return new ReviewReportDetailDto(
                report.getId(),
                review.getMovieTitle(),
                review.getContent(),
                review.getScore(),
                review.getUser().getNickname(),
                ReviewReportReason.fromCode(report.getReason()).label(),
                report.getReporter().getNickname(),
                report.getCreatedAt()
        );
    }
}
