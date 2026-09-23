package com.moviepick.backend.watched.dto;

import jakarta.validation.constraints.NotBlank;

public record WatchedMovieRequest(
        @NotBlank String movieId,
        @NotBlank String movieTitle,
        String posterUrl
) {
}
