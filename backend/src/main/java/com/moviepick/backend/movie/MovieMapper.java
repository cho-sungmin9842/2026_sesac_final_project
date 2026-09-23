package com.moviepick.backend.movie;

import com.moviepick.backend.kmdb.KmdbTextUtils;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbPerson;
import com.moviepick.backend.kmdb.dto.KmdbPlot;
import com.moviepick.backend.movie.dto.ActorDto;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import com.moviepick.backend.movie.dto.TrailerDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MovieMapper {

    public MovieSummaryDto toSummary(KmdbMovieItem item) {
        return toSummary(item, null, 0);
    }

    // 평점(리뷰 DB 집계)은 KMDB 응답만으로는 알 수 없어서, MovieService가 조회한 값을 나중에 끼워 넣습니다.
    public MovieSummaryDto toSummary(KmdbMovieItem item, Double averageScore, int reviewCount) {
        return new MovieSummaryDto(
                toId(item),
                KmdbTextUtils.clean(item.getTitle()),
                KmdbTextUtils.clean(item.getTitleEng()),
                toYear(item.getProdYear()),
                KmdbTextUtils.splitComma(item.getGenre()),
                posterOrStillUrl(item),
                KmdbTextUtils.clean(item.getRating()),
                toRuntime(item.getRuntime()),
                averageScore,
                reviewCount
        );
    }

    public MovieDetailDto toDetail(KmdbMovieItem item) {
        return toDetail(item, null, 0);
    }

    // 평점(리뷰 DB 집계)은 KMDB 응답만으로는 알 수 없어서, MovieService가 조회한 값을 나중에 끼워 넣습니다(toSummary와 동일한 패턴).
    public MovieDetailDto toDetail(KmdbMovieItem item, Double averageScore, int reviewCount) {
        List<String> directors = item.getDirectors() == null || item.getDirectors().getDirector() == null
                ? List.of()
                : item.getDirectors().getDirector().stream().map(KmdbPerson::displayName).filter(java.util.Objects::nonNull).toList();

        List<ActorDto> actors = item.getActors() == null || item.getActors().getActor() == null
                ? List.of()
                : item.getActors().getActor().stream()
                        .map(actor -> new ActorDto(actor.displayName(), KmdbTextUtils.clean(actor.getCast())))
                        .filter(actor -> actor.name() != null)
                        .toList();

        String plot = item.getPlots() == null || item.getPlots().getPlot() == null
                ? null
                : item.getPlots().getPlot().stream()
                        .map(KmdbPlot::cleanText)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        List<TrailerDto> trailers = extractTrailers(item);

        return new MovieDetailDto(
                toId(item),
                KmdbTextUtils.clean(item.getTitle()),
                KmdbTextUtils.clean(item.getTitleEng()),
                toYear(item.getProdYear()),
                KmdbTextUtils.splitComma(item.getGenre()),
                toRuntime(item.getRuntime()),
                KmdbTextUtils.clean(item.getRating()),
                KmdbTextUtils.clean(item.getNation()),
                KmdbTextUtils.clean(item.getCompany()),
                directors,
                actors,
                plot,
                posterOrStillUrl(item),
                KmdbTextUtils.splitPipe(item.getStlls()),
                KmdbTextUtils.splitComma(item.getKeywords()),
                trailers,
                averageScore,
                reviewCount
        );
    }

    // 상세 페이지 포스터의 예고편 링크용 - 목록/검색 카드는 더 이상 예고편 정보를 쓰지 않아 toDetail()만 사용합니다.
    private List<TrailerDto> extractTrailers(KmdbMovieItem item) {
        return item.getVods() == null || item.getVods().getVod() == null
                ? List.of()
                : item.getVods().getVod().stream()
                        .filter(vod -> vod.cleanUrl() != null)
                        .map(vod -> new TrailerDto(vod.cleanClass(), vod.cleanUrl()))
                        .toList();
    }

    // KMDB에 포스터(posters)가 없는 영화가 꽤 많아서, 없을 때는 스틸컷(stlls) 첫 장을 대신 씁니다.
    private String posterOrStillUrl(KmdbMovieItem item) {
        List<String> posters = KmdbTextUtils.splitPipe(item.getPosters());
        if (!posters.isEmpty()) {
            return posters.get(0);
        }
        List<String> stills = KmdbTextUtils.splitPipe(item.getStlls());
        return stills.isEmpty() ? null : stills.get(0);
    }

    public String toId(KmdbMovieItem item) {
        return item.getMovieId() + "_" + item.getMovieSeq();
    }

    private Integer toYear(String prodYear) {
        String cleaned = KmdbTextUtils.clean(prodYear);
        if (cleaned == null || !cleaned.matches("\\d{4}")) {
            return null;
        }
        return Integer.parseInt(cleaned);
    }

    private Integer toRuntime(String runtime) {
        String cleaned = KmdbTextUtils.clean(runtime);
        if (cleaned == null || !cleaned.matches("\\d+")) {
            return null;
        }
        return Integer.parseInt(cleaned);
    }
}
