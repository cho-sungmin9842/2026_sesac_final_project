package com.moviepick.backend.download.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DownloadRequest(
        @NotBlank String movieId,
        @NotBlank String movieTitle,
        String posterUrl,
        @Min(0) int sizeMb
) {
}
