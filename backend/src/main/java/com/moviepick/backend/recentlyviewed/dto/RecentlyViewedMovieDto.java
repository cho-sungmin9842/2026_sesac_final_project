package com.moviepick.backend.recentlyviewed.dto;

import com.moviepick.backend.recentlyviewed.RecentlyViewedMovie;

import java.time.LocalDateTime;

public record RecentlyViewedMovieDto(
        String movieId,
        String movieTitle,
        String posterUrl,
        LocalDateTime viewedAt
) {
    // movieTitle: 화면에 보여줄 제목(movies 기준으로 맞춘 값). 저장돼 있던 제목과 다를 수 있어서 따로 받습니다.
    public static RecentlyViewedMovieDto from(RecentlyViewedMovie entity, String movieTitle) {
        return new RecentlyViewedMovieDto(
                entity.getMovieId(),
                movieTitle,
                entity.getPosterUrl(),
                entity.getViewedAt()
        );
    }
}
