package com.moviepick.backend.movie.dto;

import java.util.List;

/**
 * 목록/검색 화면에서 쓰는 요약 정보.
 * KMDB는 TMDB식 평점(별점)을 제공하지 않으므로, 별점을 보여주려면 자체 리뷰 평점을 별도로 합산해야 합니다.
 */
public record MovieSummaryDto(
        String id,
        String title,
        String englishTitle,
        Integer year,
        List<String> genres,
        String posterUrl,
        String ageRating,
        Integer runtimeMinutes
) {
}
