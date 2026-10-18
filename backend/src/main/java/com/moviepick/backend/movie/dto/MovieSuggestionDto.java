package com.moviepick.backend.movie.dto;

/**
 * 헤더 검색창 자동완성 후보. 목록/검색 결과(MovieSummaryDto)와 달리 드롭다운에 바로 보여줄 최소
 * 정보(제목/연도/포스터)만 담습니다 - 평점·장르 등은 자동완성 단계에서는 필요 없습니다.
 */
public record MovieSuggestionDto(
        String id,
        String title,
        Integer year,
        String posterUrl
) {
}
