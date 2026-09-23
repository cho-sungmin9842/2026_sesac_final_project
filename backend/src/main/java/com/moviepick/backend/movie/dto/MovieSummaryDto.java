package com.moviepick.backend.movie.dto;

import java.util.List;

/**
 * 목록/검색 화면에서 쓰는 요약 정보.
 * KMDB는 TMDB식 평점(별점)을 제공하지 않으므로, 별점은 우리 DB의 리뷰 평점을 합산해서 붙입니다
 * (averageScore/reviewCount, 리뷰가 하나도 없으면 averageScore는 null).
 */
public record MovieSummaryDto(
        String id,
        String title,
        String englishTitle,
        Integer year,
        List<String> genres,
        String posterUrl,
        String ageRating,
        Integer runtimeMinutes,
        Double averageScore,
        int reviewCount
) {
}
