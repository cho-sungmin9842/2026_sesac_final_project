package com.moviepick.backend.movie;

import com.moviepick.backend.movie.dto.ActorDto;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import com.moviepick.backend.movie.dto.TrailerDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * KMDB에서 한 번이라도 조회된 영화를 그대로 저장해두는 캐시입니다(평점/리뷰수는 저희 DB 리뷰 데이터로
 * 항상 별도 계산하므로 여기 저장하지 않습니다). 목록/검색 결과가 내려올 때는 요약 정보만 채워지고
 * (hasDetail=false), 상세 페이지를 실제로 열어봐야 나머지 상세 필드까지 채워집니다(hasDetail=true).
 */
@Entity
@Table(name = "movies")
@Getter
public class Movie {

    // 배우 이름/배역, 예고편 라벨/URL을 한 컬럼에 이어붙일 때 쓰는 구분자입니다. URL 등 실제 값에는 절대
    // 나타나지 않는 제어 문자라 "|"나 ":"와 달리 URL의 "https://" 콜론과 충돌할 위험이 없습니다.
    private static final String FIELD_SEP = "\u0001";
    private static final String ENTRY_SEP = "\u0002";

    @Id
    @Column(name = "movie_id", length = 64)
    private String movieId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "english_title", length = 255)
    private String englishTitle;

    @Column(name = "year")
    private Integer year;

    @Column(name = "genres", length = 255)
    private String genres;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "age_rating", length = 50)
    private String ageRating;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "nation", length = 100)
    private String nation;

    @Column(name = "company", length = 255)
    private String company;

    @Column(name = "directors", length = 500)
    private String directors;

    @Column(name = "actors", columnDefinition = "TEXT")
    private String actors;

    @Column(name = "plot", columnDefinition = "TEXT")
    private String plot;

    @Column(name = "poster_urls", columnDefinition = "TEXT")
    private String posterUrls;

    @Column(name = "still_urls", columnDefinition = "TEXT")
    private String stillUrls;

    @Column(name = "keywords", length = 500)
    private String keywords;

    @Column(name = "trailers", columnDefinition = "TEXT")
    private String trailers;

    @Column(name = "has_detail", nullable = false)
    private boolean hasDetail;

    @Column(name = "cached_at", nullable = false)
    private LocalDateTime cachedAt;

    protected Movie() {
    }

    // 검색/목록 결과로 받은 요약 정보만 채워 새 캐시 행을 만듭니다.
    static Movie fromSummary(MovieSummaryDto dto) {
        Movie movie = new Movie();
        movie.movieId = dto.id();
        movie.applySummary(dto);
        return movie;
    }

    void applySummary(MovieSummaryDto dto) {
        this.title = dto.title();
        this.englishTitle = dto.englishTitle();
        this.year = dto.year();
        this.genres = join(dto.genres());
        this.posterUrl = dto.posterUrl();
        this.ageRating = dto.ageRating();
        this.runtimeMinutes = dto.runtimeMinutes();
        this.cachedAt = LocalDateTime.now();
    }

    // 상세 페이지 조회 결과로 나머지 필드까지 전부 채웁니다.
    void applyDetail(MovieDetailDto dto) {
        this.title = dto.title();
        this.englishTitle = dto.englishTitle();
        this.year = dto.year();
        this.genres = join(dto.genres());
        this.runtimeMinutes = dto.runtimeMinutes();
        this.ageRating = dto.ageRating();
        this.posterUrl = dto.posterUrl();
        this.nation = dto.nation();
        this.company = dto.company();
        this.directors = join(dto.directors());
        this.actors = dto.actors().stream()
                .map(actor -> nullToEmpty(actor.name()) + FIELD_SEP + nullToEmpty(actor.role()))
                .collect(Collectors.joining(ENTRY_SEP));
        this.plot = dto.plot();
        this.posterUrls = String.join("|", dto.posterUrls());
        this.stillUrls = String.join("|", dto.stillUrls());
        this.keywords = join(dto.keywords());
        this.trailers = dto.trailers().stream()
                .map(trailer -> nullToEmpty(trailer.label()) + FIELD_SEP + nullToEmpty(trailer.url()))
                .collect(Collectors.joining(ENTRY_SEP));
        this.hasDetail = true;
        this.cachedAt = LocalDateTime.now();
    }

    MovieSummaryDto toSummaryDto(Double averageScore, int reviewCount) {
        return new MovieSummaryDto(movieId, title, englishTitle, year, split(genres), posterUrl, ageRating, runtimeMinutes, averageScore, reviewCount);
    }

    MovieDetailDto toDetailDto(Double averageScore, int reviewCount) {
        List<ActorDto> actorDtos = actors == null || actors.isBlank()
                ? List.of()
                : List.of(actors.split(ENTRY_SEP)).stream()
                        .map(entry -> {
                            String[] parts = entry.split(FIELD_SEP, -1);
                            return new ActorDto(emptyToNull(parts[0]), parts.length > 1 ? emptyToNull(parts[1]) : null);
                        })
                        .toList();

        List<TrailerDto> trailerDtos = trailers == null || trailers.isBlank()
                ? List.of()
                : List.of(trailers.split(ENTRY_SEP)).stream()
                        .map(entry -> {
                            String[] parts = entry.split(FIELD_SEP, -1);
                            return new TrailerDto(emptyToNull(parts[0]), parts.length > 1 ? emptyToNull(parts[1]) : null);
                        })
                        .toList();

        return new MovieDetailDto(
                movieId, title, englishTitle, year, split(genres), runtimeMinutes, ageRating, nation, company,
                split(directors), actorDtos, plot, posterUrl, splitPipe(posterUrls), splitPipe(stillUrls),
                split(keywords), trailerDtos, averageScore, reviewCount
        );
    }

    private static String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }

    private static List<String> splitPipe(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\|"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
