package com.moviepick.backend.movie;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.kmdb.KmdbClient;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbSearchResponse;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSearchResultDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import com.moviepick.backend.review.RatingSummary;
import com.moviepick.backend.review.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MovieService {

    private static final DateTimeFormatter KMDB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // KMDB는 한 번에 최대 약 500건까지만 내려줍니다(1000+는 500 에러) - 정렬을 위해 여러 페이지 분량을 한 번에 받아옵니다.
    private static final int KMDB_MAX_LIST_COUNT = 500;

    private final KmdbClient kmdbClient;
    private final MovieMapper movieMapper;
    private final ReviewService reviewService;

    public MovieService(KmdbClient kmdbClient, MovieMapper movieMapper, ReviewService reviewService) {
        this.kmdbClient = kmdbClient;
        this.movieMapper = movieMapper;
        this.reviewService = reviewService;
    }

    public MovieSearchResultDto search(String query, List<String> genres, String year, String sort, int page, int pageSize) {
        String releaseDts = year == null || year.isBlank() ? null : year + "0101";
        String releaseDte = year == null || year.isBlank() ? null : year + "1231";
        List<String> cleanGenres = genres == null ? List.of() : genres.stream().filter(g -> g != null && !g.isBlank()).toList();

        // KMDB 검색 API에는 정렬 파라미터가 없어서, "최신순"/"이름순"이 카탈로그 전체 기준으로 맞으려면
        // 한 페이지 분량(pageSize)만 받아서 정렬해선 안 되고, KMDB 한도(500건) 안에서 최대한 많이 받아와
        // 그 안에서 정렬한 뒤 이번 페이지 구간만 잘라내야 합니다. (page*pageSize만큼만 받으면 1페이지 요청 시
        // 정확히 pageSize개만 받아와 그 안에서만 재배열하는 꼴이 되어, 화면엔 "최신순"이라 써있어도 KMDB가 내려준
        // 순서에서 우연히 걸린 것들끼리만 정렬된 것처럼 보이는 원래 버그가 그대로 재현됩니다.)
        int rawListCount = KMDB_MAX_LIST_COUNT;

        List<MovieSummaryDto> allMovies;
        Integer totalCountFromKmdb;

        if (cleanGenres.size() > 1) {
            // KMDB genre 파라미터는 한 번에 장르 하나만 받기 때문에(부분일치), 선호 장르가 여러 개(홈 화면
            // "취향저격 신작 (장르: 코미디, 스릴러, 액션)"처럼)로 필터링할 땐 장르별로 따로 호출한 뒤 합쳐야 합니다.
            allMovies = searchByMultipleGenres(query, cleanGenres, releaseDts, releaseDte, rawListCount);
            totalCountFromKmdb = null; // 합친 뒤 중복 제거한 실제 개수를 아래에서 씁니다.
        } else {
            String genre = cleanGenres.isEmpty() ? null : cleanGenres.get(0);
            KmdbSearchResponse response = kmdbClient.searchByTitle(query, genre, releaseDts, releaseDte, rawListCount, 0);

            // 검색창은 "영화, 배우, 감독 검색"을 안내하므로, 제목으로 못 찾으면 배우 → 감독 이름으로도 찾아봅니다.
            if (isBlank(response) && query != null && !query.isBlank()) {
                KmdbSearchResponse byActor =
                        kmdbClient.searchByActor(query, genre, releaseDts, releaseDte, rawListCount, 0);
                response = isBlank(byActor)
                        ? kmdbClient.searchByDirector(query, genre, releaseDts, releaseDte, rawListCount, 0)
                        : byActor;
            }

            allMovies = toRatedSummaries(response.allItems());
            totalCountFromKmdb = response.getTotalCount();
        }

        allMovies = sortMovies(allMovies, sort);

        int fromIndex = Math.min((page - 1) * pageSize, allMovies.size());
        int toIndex = Math.min(page * pageSize, allMovies.size());
        List<MovieSummaryDto> movies = allMovies.subList(fromIndex, toIndex);

        int totalCount = totalCountFromKmdb == null ? allMovies.size() : totalCountFromKmdb;
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) pageSize);

        return new MovieSearchResultDto(movies, page, pageSize, totalCount, totalPages);
    }

    private List<MovieSummaryDto> searchByMultipleGenres(
            String query, List<String> genres, String releaseDts, String releaseDte, int rawListCount
    ) {
        Map<String, MovieSummaryDto> merged = new LinkedHashMap<>();
        for (String genre : genres) {
            KmdbSearchResponse response = kmdbClient.searchByTitle(query, genre, releaseDts, releaseDte, rawListCount, 0);
            for (MovieSummaryDto movie : toRatedSummaries(response.allItems())) {
                merged.putIfAbsent(movie.id(), movie);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<MovieSummaryDto> sortMovies(List<MovieSummaryDto> movies, String sort) {
        Comparator<MovieSummaryDto> comparator = "name".equals(sort)
                ? Comparator.comparing(MovieSummaryDto::title, Collator.getInstance(Locale.KOREAN))
                : Comparator.comparing((MovieSummaryDto movie) -> movie.year() == null ? Integer.MIN_VALUE : movie.year())
                        .reversed();
        return movies.stream().sorted(comparator).toList();
    }

    // 예매 화면의 "상영중인 영화" 목록 - 실제 상영 스케줄 API가 없어서, 최근 2개월 내 개봉일자(releaseDts~releaseDte)로
    // 대신합니다. 접속 시점 기준으로 매번 계산하므로 별도 배치/스케줄러 없이 항상 최신 범위를 봅니다.
    public MovieSearchResultDto getNowShowing(int listCount) {
        LocalDate today = LocalDate.now();
        String releaseDte = today.format(KMDB_DATE_FORMAT);
        String releaseDts = today.minusMonths(2).format(KMDB_DATE_FORMAT);

        KmdbSearchResponse response = kmdbClient.searchByTitle("", null, releaseDts, releaseDte, listCount, 0);
        List<MovieSummaryDto> movies = toRatedSummaries(response.allItems());
        int totalCount = response.getTotalCount() == null ? movies.size() : response.getTotalCount();

        return new MovieSearchResultDto(movies, 1, listCount, totalCount, totalCount == 0 ? 0 : 1);
    }

    private List<MovieSummaryDto> toRatedSummaries(List<KmdbMovieItem> items) {
        List<String> movieIds = items.stream().map(movieMapper::toId).toList();
        Map<String, RatingSummary> ratings = reviewService.getRatingSummaries(movieIds);

        return items.stream()
                .map(item -> {
                    RatingSummary rating = ratings.get(movieMapper.toId(item));
                    return rating == null
                            ? movieMapper.toSummary(item)
                            : movieMapper.toSummary(item, rating.averageScore(), (int) rating.reviewCount());
                })
                .toList();
    }

    private boolean isBlank(KmdbSearchResponse response) {
        Integer totalCount = response.getTotalCount();
        return totalCount == null || totalCount == 0;
    }

    public MovieDetailDto getDetail(String movieId, String movieSeq) {
        List<KmdbMovieItem> items = kmdbClient.findByMovieId(movieId, movieSeq).allItems();
        KmdbMovieItem item = items.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException("영화를 찾을 수 없습니다: " + movieId + "_" + movieSeq, HttpStatus.NOT_FOUND));

        RatingSummary rating = reviewService.getRatingSummaries(List.of(movieMapper.toId(item))).get(movieMapper.toId(item));
        return rating == null
                ? movieMapper.toDetail(item)
                : movieMapper.toDetail(item, rating.averageScore(), (int) rating.reviewCount());
    }
}
