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
        return new ReviewDto(
                review.getId(),
                review.getMovieId(),
                review.getMovieTitle(),
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getScore(),
                review.getContent(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
