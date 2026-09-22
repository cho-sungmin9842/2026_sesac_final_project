package com.moviepick.backend.movie;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.kmdb.KmdbClient;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbSearchResponse;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSearchResultDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MovieService {

    private final KmdbClient kmdbClient;
    private final MovieMapper movieMapper;

    public MovieService(KmdbClient kmdbClient, MovieMapper movieMapper) {
        this.kmdbClient = kmdbClient;
        this.movieMapper = movieMapper;
    }

    public MovieSearchResultDto search(String query, String genre, String year, int page, int pageSize) {
        int startCount = (page - 1) * pageSize;
        String releaseDts = year == null || year.isBlank() ? null : year + "0101";
        String releaseDte = year == null || year.isBlank() ? null : year + "1231";

        KmdbSearchResponse response = kmdbClient.searchByTitle(query, genre, releaseDts, releaseDte, pageSize, startCount);

        // 검색창은 "영화, 배우, 감독 검색"을 안내하므로, 제목으로 못 찾으면 배우 → 감독 이름으로도 찾아봅니다.
        if (isBlank(response) && query != null && !query.isBlank()) {
            KmdbSearchResponse byActor =
                    kmdbClient.searchByActor(query, genre, releaseDts, releaseDte, pageSize, startCount);
            response = isBlank(byActor)
                    ? kmdbClient.searchByDirector(query, genre, releaseDts, releaseDte, pageSize, startCount)
                    : byActor;
        }

        List<MovieSummaryDto> movies = response.allItems().stream().map(movieMapper::toSummary).toList();
        int totalCount = response.getTotalCount() == null ? movies.size() : response.getTotalCount();
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) pageSize);

        return new MovieSearchResultDto(movies, page, pageSize, totalCount, totalPages);
    }

    private boolean isBlank(KmdbSearchResponse response) {
        Integer totalCount = response.getTotalCount();
        return totalCount == null || totalCount == 0;
    }

    public MovieDetailDto getDetail(String movieId, String movieSeq) {
        List<KmdbMovieItem> items = kmdbClient.findByMovieId(movieId, movieSeq).allItems();
        return items.stream()
                .findFirst()
                .map(movieMapper::toDetail)
                .orElseThrow(() -> new ApiException("영화를 찾을 수 없습니다: " + movieId + "_" + movieSeq, HttpStatus.NOT_FOUND));
    }
}
