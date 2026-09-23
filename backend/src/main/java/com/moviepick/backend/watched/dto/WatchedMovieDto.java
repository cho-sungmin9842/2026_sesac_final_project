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
        return new WatchedMovieDto(
                watchedMovie.getMovieId(),
                watchedMovie.getMovieTitle(),
                watchedMovie.getPosterUrl(),
                watchedMovie.getWatchedAt()
        );
    }
}
