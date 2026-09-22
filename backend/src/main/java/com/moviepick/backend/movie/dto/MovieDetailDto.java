package com.moviepick.backend.movie.dto;

import java.util.List;

public record MovieDetailDto(
        String id,
        String title,
        String englishTitle,
        Integer year,
        List<String> genres,
        Integer runtimeMinutes,
        String ageRating,
        String nation,
        String company,
        List<String> directors,
        List<ActorDto> actors,
        String plot,
        String posterUrl,
        List<String> stillUrls,
        List<String> keywords,
        List<TrailerDto> trailers
) {
}
