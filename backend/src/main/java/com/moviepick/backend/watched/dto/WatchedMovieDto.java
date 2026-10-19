package com.moviepick.backend.watched.dto;

import com.moviepick.backend.watched.WatchedMovie;

import java.time.LocalDateTime;

public record WatchedMovieDto(
        String movieId,
        String movieTitle,
        String posterUrl,
        LocalDateTime watchedAt
) {
    public static WatchedMovieDto from(WatchedMovie watchedMovie) {
        return from(watchedMovie, watchedMovie.getMovieTitle());
    }

    // movieTitle: 화면에 보여줄 제목(movies 기준으로 맞춘 값). 저장돼 있던 제목과 다를 수 있어서 따로 받습니다.
    public static WatchedMovieDto from(WatchedMovie watchedMovie, String movieTitle) {
        return new WatchedMovieDto(
                watchedMovie.getMovieId(),
                movieTitle,
                watchedMovie.getPosterUrl(),
                watchedMovie.getWatchedAt()
        );
    }
}
