package com.moviepick.backend.movie;

import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * KMDB에서 받아온 영화 데이터를 movies 테이블에 캐싱합니다.
 * 검색/목록 API가 호출될 때마다 KMDB를 다시 두드리지 않도록, 이미 캐시된 것은 DB를 우선 사용하고
 * 없을 때만 KMDB를 호출한 뒤 저장합니다(사용자가 실제로 조회한 영화만 점진적으로 쌓입니다).
 */
@Service
public class MovieCacheService {

    private final MovieRepository movieRepository;

    public MovieCacheService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    // 상세 페이지 캐시 히트 여부 확인 - 요약 정보만 캐시된 행(hasDetail=false)은 상세를 대신할 수 없습니다.
    public Optional<MovieDetailDto> findCachedDetail(String movieId, Double averageScore, int reviewCount) {
        return movieRepository.findById(movieId)
                .filter(Movie::isHasDetail)
                .map(movie -> movie.toDetailDto(averageScore, reviewCount));
    }

    public Optional<MovieSummaryDto> findCachedSummary(String movieId, Double averageScore, int reviewCount) {
        return movieRepository.findById(movieId).map(movie -> movie.toSummaryDto(averageScore, reviewCount));
    }

    // 요청한 순서 그대로, 캐시에 있는 것만 골라 반환합니다(상영중 스냅샷처럼 순서가 중요한 경우에 씁니다).
    public List<MovieSummaryDto> findCachedSummariesInOrder(List<String> movieIds, Map<String, MovieSummaryRating> ratings) {
        Map<String, Movie> byId = movieRepository.findAllById(movieIds).stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity()));
        return movieIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(movie -> {
                    MovieSummaryRating rating = ratings.get(movie.getMovieId());
                    return rating == null
                            ? movie.toSummaryDto(null, 0)
                            : movie.toSummaryDto(rating.averageScore(), rating.reviewCount());
                })
                .toList();
    }

    // 검색/목록 응답으로 받은 요약 정보를 캐시에 반영합니다. 이미 상세까지 캐시된 영화는 상세 필드를 그대로 두고
    // 요약 필드만 최신화합니다.
    public void upsertSummaries(List<MovieSummaryDto> summaries) {
        if (summaries.isEmpty()) {
            return;
        }
        List<String> ids = summaries.stream().map(MovieSummaryDto::id).toList();
        Map<String, Movie> existing = movieRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity()));

        List<Movie> toSave = summaries.stream()
                .map(dto -> {
                    Movie movie = existing.get(dto.id());
                    if (movie == null) {
                        return Movie.fromSummary(dto);
                    }
                    movie.applySummary(dto);
                    return movie;
                })
                .toList();
        movieRepository.saveAll(toSave);
    }

    public void upsertDetail(MovieDetailDto detail) {
        Movie movie = movieRepository.findById(detail.id()).orElseGet(() -> Movie.fromSummary(toBareSummary(detail)));
        movie.applyDetail(detail);
        movieRepository.save(movie);
    }

    private MovieSummaryDto toBareSummary(MovieDetailDto detail) {
        return new MovieSummaryDto(
                detail.id(), detail.title(), detail.englishTitle(), detail.year(), detail.genres(),
                detail.posterUrl(), detail.ageRating(), detail.runtimeMinutes(), null, 0
        );
    }

    public record MovieSummaryRating(Double averageScore, int reviewCount) {
    }
}
