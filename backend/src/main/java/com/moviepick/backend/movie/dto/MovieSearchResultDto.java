package com.moviepick.backend.movie.dto;

import java.util.List;

public record MovieSearchResultDto(
        List<MovieSummaryDto> movies,
        int page,
        int pageSize,
        int totalCount,
        int totalPages
) {
}
