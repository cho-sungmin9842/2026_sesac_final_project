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
        LocalDateTime updatedAt
) {
    public static ReviewDto from(Review review) {
        return from(review, review.getMovieTitle());
    }

    // movieTitle: 화면에 보여줄 제목(movies 기준으로 맞춘 값). 저장돼 있던 제목과 다를 수 있어서 따로 받습니다.
    public static ReviewDto from(Review review, String movieTitle) {
        return new ReviewDto(
                review.getId(),
                review.getMovieId(),
                movieTitle,
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getScore(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
