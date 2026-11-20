package com.moviepick.backend.review.dto;

import com.moviepick.backend.review.ReviewDeletionLog;

import java.time.LocalDateTime;

// 관리자 "리뷰 관리" 화면의 "삭제 이력" 탭용.
public record ReviewDeletionLogDto(
        Long id,
        Long reviewId,
        String movieTitle,
        String reviewAuthorNickname,
        String reviewContent,
        Integer reviewScore,
        String reportReasons,
        String deletedByAdminNickname,
        LocalDateTime deletedAt
) {
    public static ReviewDeletionLogDto from(ReviewDeletionLog log) {
        return new ReviewDeletionLogDto(
                log.getId(),
                log.getReviewId(),
                log.getMovieTitle(),
                log.getReviewAuthorNickname(),
                log.getReviewContent(),
                log.getReviewScore(),
                log.getReportReasons(),
                log.getDeletedByAdminNickname(),
                log.getDeletedAt()
        );
    }
}
