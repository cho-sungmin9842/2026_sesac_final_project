package com.moviepick.backend.recentlyviewed.dto;

import jakarta.validation.constraints.NotBlank;

public record RecentlyViewedMovieRequest(
        @NotBlank String movieId,
        @NotBlank String movieTitle,
        String posterUrl
) {
}
