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
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long helpfulCount,
        boolean helpfulByMe
) {
    public static ReviewDto from(Review review) {
        return from(review, review.getMovieTitle(), 0, false);
    }

    // movieTitle: 화면에 보여줄 제목(movies 기준으로 맞춘 값). 저장돼 있던 제목과 다를 수 있어서 따로 받습니다.
    public static ReviewDto from(Review review, String movieTitle) {
        return from(review, movieTitle, 0, false);
    }

    // 영화 상세 화면의 리뷰 목록(ReviewService.list)에서만 추천 수/내 추천 여부를 실제 값으로 채웁니다
    // (마이페이지 "내가 쓴 리뷰", 관리자 리뷰 목록은 본인 리뷰거나 추천 UI가 없어 0/false로 둡니다).
    public static ReviewDto from(Review review, String movieTitle, long helpfulCount, boolean helpfulByMe) {
        return new ReviewDto(
                review.getId(),
                review.getMovieId(),
                movieTitle,
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getScore(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                helpfulCount,
                helpfulByMe
        );
    }
}
