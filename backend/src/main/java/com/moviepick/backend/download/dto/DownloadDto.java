package com.moviepick.backend.download.dto;

import com.moviepick.backend.download.Download;

import java.time.LocalDateTime;

public record DownloadDto(
        String movieId,
        String movieTitle,
        String posterUrl,
        int sizeMb,
        LocalDateTime downloadedAt
) {
    public static DownloadDto from(Download download) {
        return new DownloadDto(
                download.getMovieId(),
                download.getMovieTitle(),
                download.getPosterUrl(),
                download.getSizeMb(),
                download.getDownloadedAt()
        );
    }
}
