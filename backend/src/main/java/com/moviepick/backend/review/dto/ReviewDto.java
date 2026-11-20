package com.moviepick.backend.review.dto;

import com.moviepick.backend.review.Review;

import java.time.LocalDateTime;

public record ReviewDto(
        Long id,
        String movieId,
        String movieTitle,
        Long userId,
        String authorNickname,
        Integer score,
        String content,
        String photoUrl,
        String photoOriginalName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long helpfulCount,
        boolean helpfulByMe,
        long reportCount
) {
    public static ReviewDto from(Review review) {
        return from(review, review.getMovieTitle(), 0, false);
    }

    // movieTitle: 화면에 보여줄 제목(movies 기준으로 맞춘 값). 저장돼 있던 제목과 다를 수 있어서 따로 받습니다.
    public static ReviewDto from(Review review, String movieTitle) {
        return from(review, movieTitle, 0, false);
    }

    // 영화 상세 화면의 리뷰 목록(ReviewService.list)에서만 추천 수/내 추천 여부를 실제 값으로 채웁니다
    // (마이페이지 "내가 쓴 리뷰"는 본인 리뷰거나 추천 UI가 없어 0/false로 둡니다). 신고 수(reportCount)는
    // 관리자 "리뷰 관리" 화면(ReviewService.listAllForAdmin)에서만 실제 값으로 채우고, 그 외에는 0입니다 -
    // 신고 건수는 관리자만 볼 수 있는 정보라 일반 사용자 화면에는 아예 내려가지 않습니다.
    public static ReviewDto from(Review review, String movieTitle, long helpfulCount, boolean helpfulByMe) {
        return from(review, movieTitle, helpfulCount, helpfulByMe, 0);
    }

    public static ReviewDto from(
            Review review, String movieTitle, long helpfulCount, boolean helpfulByMe, long reportCount
    ) {
        return new ReviewDto(
                review.getId(),
                review.getMovieId(),
                movieTitle,
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getScore(),
                review.getContent(),
                review.getPhotoUrl(),
                review.getPhotoOriginalName(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                helpfulCount,
                helpfulByMe,
                reportCount
        );
    }
}
