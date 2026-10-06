package com.moviepick.backend.movie;

import com.moviepick.backend.kmdb.KmdbTextUtils;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbPerson;
import com.moviepick.backend.kmdb.dto.KmdbPlot;
import com.moviepick.backend.kmdb.dto.KmdbRating;
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
                KmdbTextUtils.cleanTitle(item.getTitle()),
                KmdbTextUtils.clean(item.getTitleEng()),
                toYear(item),
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
                KmdbTextUtils.cleanTitle(item.getTitle()),
                KmdbTextUtils.clean(item.getTitleEng()),
                toYear(item),
                KmdbTextUtils.splitComma(item.getGenre()),
                toRuntime(item.getRuntime()),
                KmdbTextUtils.clean(item.getRating()),
                KmdbTextUtils.clean(item.getNation()),
                KmdbTextUtils.clean(item.getCompany()),
                directors,
                actors,
                plot,
                posterOrStillUrl(item),
                posterUrls(item),
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
        List<String> posters = posterUrls(item);
        return posters.isEmpty() ? null : posters.get(0);
    }

    // 상세 페이지 포스터 갤러리용 - posters가 "|"로 여러 장 올 수 있어서 그대로 다 넘깁니다(없으면 stlls로 대체).
    private List<String> posterUrls(KmdbMovieItem item) {
        List<String> posters = KmdbTextUtils.splitPipe(item.getPosters());
        return posters.isEmpty() ? KmdbTextUtils.splitPipe(item.getStlls()) : posters;
    }

    public String toId(KmdbMovieItem item) {
        return item.getMovieId() + "_" + item.getMovieSeq();
    }

    // 개봉연도는 ratings.rating[].releaseDate(yyyyMMdd)의 앞 4자리(yyyy)를 씁니다. 값이 없는 옛날/소규모
    // 개봉작은 releaseDate가 비어있는 경우가 있어, 그때만 prodYear(제작연도)로 대신합니다.
    private Integer toYear(KmdbMovieItem item) {
        String releaseYear = firstReleaseYear(item);
        if (releaseYear != null) {
            return Integer.parseInt(releaseYear);
        }
        String prodYear = KmdbTextUtils.clean(item.getProdYear());
        return prodYear != null && prodYear.matches("\\d{4}") ? Integer.parseInt(prodYear) : null;
    }

    // 재개봉/특별상영이 여러 건 있는 영화는 KMDB가 releaseDate 하나에 "0||20260910"처럼 "|"로 여러 값을
    // 이어 붙여서 내려주기도 합니다(예: 침투부부). 그대로 앞 4자리를 자르면 "0||2" 같은 값이 나와 파싱에
    // 실패하므로, "|"로 먼저 쪼갠 뒤 실제 4자리 연도로 보이는 첫 값만 씁니다.
    private String firstReleaseYear(KmdbMovieItem item) {
        if (item.getRatings() == null || item.getRatings().getRating() == null) {
            return null;
        }
        return item.getRatings().getRating().stream()
                .map(KmdbRating::cleanReleaseDate)
                .filter(java.util.Objects::nonNull)
                .flatMap(date -> java.util.Arrays.stream(date.split("\\|")))
                .map(String::trim)
                .filter(part -> part.matches("\\d{4}\\d*") && !part.substring(0, 4).equals("0000"))
                .map(part -> part.substring(0, 4))
                .findFirst()
                .orElse(null);
    }

    private Integer toRuntime(String runtime) {
        return KmdbTextUtils.parseRuntimeMinutes(runtime);
    }
}
