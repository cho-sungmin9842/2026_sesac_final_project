package com.moviepick.backend.movie;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.kmdb.KmdbClient;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbPerson;
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
    // 제목 없는 항목이 껴서 페이지가 모자라면 이어지는 구간을 추가로 더 받아오는데, 그 재시도 최대 횟수입니다
    // (제목 없는 항목은 아주 드물어서 1~2번이면 충분하지만, 여유 있게 잡아둡니다).
    private static final int FILL_PAGE_MAX_ATTEMPTS = 5;

    private final KmdbClient kmdbClient;
    private final MovieMapper movieMapper;
    private final ReviewService reviewService;

    public MovieService(KmdbClient kmdbClient, MovieMapper movieMapper, ReviewService reviewService) {
        this.kmdbClient = kmdbClient;
        this.movieMapper = movieMapper;
        this.reviewService = reviewService;
    }

    public MovieSearchResultDto search(String query, List<String> genres, String year, String sort, int page, int pageSize, String field) {
        String releaseDts = year == null || year.isBlank() ? null : year + "0101";
        String releaseDte = year == null || year.isBlank() ? null : year + "1231";
        List<String> cleanGenres = genres == null ? List.of() : genres.stream().filter(g -> g != null && !g.isBlank()).toList();
        boolean hasQuery = query != null && !query.isBlank();
        // 최신순 -> prodYear,1 / 이름순 -> title,0. KMDB가 직접 지원하는 정렬이라 응답 자체가 이미 정렬돼서 옵니다.
        String kmdbSort = toKmdbSort(sort);

        if (cleanGenres.size() <= 1 && !hasQuery) {
            // 검색어가 없으면(장르/연도만으로 찾아보기, "전체 영화") 뒤에서 텍스트로 한 번 더 거를 필요가 없으니,
            // KMDB의 startCount/listCount로 이번 페이지 분량만 바로 받아옵니다. 그래야 몇 만 건짜리 카탈로그도
            // "마지막 페이지"까지 정확히 이동할 수 있습니다(예전엔 항상 앞쪽 500건만 받아와 그 안에서만 페이지를
            // 나눠서, 500건 밖의 페이지로 가면 빈 목록만 보였습니다).
            String genre = cleanGenres.isEmpty() ? null : cleanGenres.get(0);
            int startCount = (page - 1) * pageSize;
            FilledPage filled = fetchFilledPage(field, query, genre, releaseDts, releaseDte, pageSize, startCount, kmdbSort);
            int totalPages = filled.totalCount() == 0 ? 0 : (int) Math.ceil(filled.totalCount() / (double) pageSize);
            return new MovieSearchResultDto(filled.movies(), page, pageSize, filled.totalCount(), totalPages);
        }

        // 검색어가 있거나 장르를 여러 개 고른 경우는 KMDB 응답을 텍스트로 한 번 더 거르거나 여러 번 합쳐야 해서,
        // 이번 페이지만 딱 집어 받아올 수 없습니다. KMDB 한도(500건) 안에서 최대한 받아와 그 안에서 이번 페이지
        // 구간만 잘라냅니다(따라서 이 경우 페이지 이동은 500건 범위 안에서만 정확합니다).
        int rawListCount = KMDB_MAX_LIST_COUNT;
        List<MovieSummaryDto> allMovies;

        if (cleanGenres.size() > 1) {
            // KMDB genre 파라미터는 한 번에 장르 하나만 받기 때문에(부분일치), 선호 장르가 여러 개(홈 화면
            // "취향저격 신작 (장르: 코미디, 스릴러, 액션)"처럼)로 필터링할 땐 장르별로 따로 호출한 뒤 합쳐야 합니다.
            allMovies = searchByMultipleGenres(query, cleanGenres, releaseDts, releaseDte, rawListCount, field, kmdbSort);
        } else {
            String genre = cleanGenres.isEmpty() ? null : cleanGenres.get(0);
            // 검색창의 영화/배우/감독 드롭다운이 고른 필드로, 해외 영화를 뺀 국내 영화만, 정렬까지 맞춰서
            // KMDB에 요청합니다(KmdbClient가 nation=대한민국을 항상 붙입니다).
            KmdbSearchResponse response = searchByField(field, query, genre, releaseDts, releaseDte, rawListCount, 0, kmdbSort);
            // KMDB는 검색어를 형태소 단위로 쪼개 부분 일치시켜 관련 없는 결과까지 섞어 내려주므로,
            // 실제로 검색어를 포함하는 항목만 한 번 더 걸러냅니다.
            List<KmdbMovieItem> matchedItems = filterByQuery(response.allItems(), field, query);
            allMovies = toRatedSummaries(matchedItems);
        }

        allMovies = sortMovies(allMovies, sort);

        int fromIndex = Math.min((page - 1) * pageSize, allMovies.size());
        int toIndex = Math.min(page * pageSize, allMovies.size());
        List<MovieSummaryDto> movies = allMovies.subList(fromIndex, toIndex);

        int totalCount = allMovies.size();
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) pageSize);

        return new MovieSearchResultDto(movies, page, pageSize, totalCount, totalPages);
    }

    private List<MovieSummaryDto> searchByMultipleGenres(
            String query, List<String> genres, String releaseDts, String releaseDte, int rawListCount, String field, String kmdbSort
    ) {
        Map<String, MovieSummaryDto> merged = new LinkedHashMap<>();
        for (String genre : genres) {
            KmdbSearchResponse response = searchByField(field, query, genre, releaseDts, releaseDte, rawListCount, 0, kmdbSort);
            List<KmdbMovieItem> matchedItems = filterByQuery(response.allItems(), field, query);
            for (MovieSummaryDto movie : toRatedSummaries(matchedItems)) {
                merged.putIfAbsent(movie.id(), movie);
            }
        }
        return new ArrayList<>(merged.values());
    }

    // KMDB는 검색어를 형태소 단위로 쪼개 부분 일치시키므로("군체" -> "군"+"군체"), 실제로 검색어 문자열을
    // 포함하는 항목만 남기도록 응답을 한 번 더 걸러냅니다. query가 비어있으면(장르/연도만으로 찾아볼 때) 거르지 않습니다.
    private List<KmdbMovieItem> filterByQuery(List<KmdbMovieItem> items, String field, String query) {
        String needle = KmdbTextUtils.clean(query);
        if (needle == null) {
            return items;
        }
        return items.stream().filter(item -> matchesQuery(item, field, needle)).toList();
    }

    private boolean matchesQuery(KmdbMovieItem item, String field, String needle) {
        return switch (field == null ? "title" : field) {
            case "actor" -> personsContain(item.getActors() == null ? null : item.getActors().getActor(), needle);
            case "director" -> personsContain(item.getDirectors() == null ? null : item.getDirectors().getDirector(), needle);
            case "keyword" -> containsIgnoreCase(item.getKeywords(), needle);
            default -> containsIgnoreCase(item.getTitle(), needle)
                    || containsIgnoreCase(item.getTitleEng(), needle)
                    || containsIgnoreCase(item.getTitleOrg(), needle);
        };
    }

    private boolean personsContain(List<KmdbPerson> persons, String needle) {
        return persons != null && persons.stream().anyMatch(person -> containsIgnoreCase(person.displayName(), needle));
    }

    private boolean containsIgnoreCase(String value, String needle) {
        String cleaned = KmdbTextUtils.clean(value);
        return cleaned != null && cleaned.toLowerCase(Locale.KOREAN).contains(needle.toLowerCase(Locale.KOREAN));
    }

    // 검색창의 영화/배우/감독 드롭다운 값을 KMDB의 실제 파라미터명(title/actor/director)에 그대로 매핑합니다.
    private KmdbSearchResponse searchByField(
            String field, String value, String genre, String releaseDts, String releaseDte, int listCount, int startCount, String sort
    ) {
        return switch (field == null ? "title" : field) {
            case "actor" -> kmdbClient.searchByActor(value, genre, releaseDts, releaseDte, listCount, startCount, sort);
            case "director" -> kmdbClient.searchByDirector(value, genre, releaseDts, releaseDte, listCount, startCount, sort);
            case "keyword" -> kmdbClient.searchByKeyword(value, genre, releaseDts, releaseDte, listCount, startCount, sort);
            default -> kmdbClient.searchByTitle(value, genre, releaseDts, releaseDte, listCount, startCount, sort);
        };
    }

    // 화면의 "최신순"/"이름순"을 KMDB가 실제로 받는 sort 파라미터 값으로 바꿉니다.
    private String toKmdbSort(String sort) {
        return "name".equals(sort) ? "title,0" : "prodYear,1";
    }

    private List<MovieSummaryDto> sortMovies(List<MovieSummaryDto> movies, String sort) {
        // title이 빈 값이라 null인 항목도 있어서(KmdbTextUtils.clean), Collator 비교 전에 null을 뒤로 뺍니다.
        Comparator<MovieSummaryDto> comparator = "name".equals(sort)
                ? Comparator.comparing(MovieSummaryDto::title, Comparator.nullsLast(Collator.getInstance(Locale.KOREAN)))
                : Comparator.comparing((MovieSummaryDto movie) -> movie.year() == null ? Integer.MIN_VALUE : movie.year())
                        .reversed();
        return movies.stream().sorted(comparator).toList();
    }

    // 예매 화면의 "상영중인 영화" 목록 - 실제 상영 스케줄 API가 없어서, 최근 4주 내 개봉일자(releaseDts~releaseDte)로
    // 대신합니다. 접속 시점 기준으로 매번 계산하므로 별도 배치/스케줄러 없이 항상 최신 범위를 봅니다.
    public MovieSearchResultDto getNowShowing(int listCount) {
        LocalDate today = LocalDate.now();
        String releaseDte = today.format(KMDB_DATE_FORMAT);
        String releaseDts = today.minusWeeks(4).format(KMDB_DATE_FORMAT);

        FilledPage filled = fetchFilledPage("title", "", null, releaseDts, releaseDte, listCount, 0, null);
        return new MovieSearchResultDto(filled.movies(), 1, listCount, filled.totalCount(), filled.totalCount() == 0 ? 0 : 1);
    }

    private record FilledPage(List<MovieSummaryDto> movies, int totalCount) {
    }

    // KMDB의 startCount/listCount로 이번 페이지 구간을 받아온 뒤 제목 없는 항목이 걸러져서 pageSize보다
    // 모자라면, 이어지는 구간을 추가로 더 받아와 정확히 pageSize개를 채웁니다. totalCount는 KMDB가 알려주는
    // 전체 건수에서 "이번 페이지를 채우는 동안" 걸러낸 개수만큼을 뺀 값입니다 - 카탈로그 전체에서 제목 없는
    // 항목이 정확히 몇 건인지는 몇 만 건을 전부 훑어야 알 수 있어 매 요청마다 계산하지 않으므로, 다른 페이지에서는
    // 이 보정이 반영되지 않을 수 있습니다(제목 없는 항목 자체가 매우 드물어 실사용에서는 거의 차이가 없습니다).
    private FilledPage fetchFilledPage(
            String field, String query, String genre, String releaseDts, String releaseDte,
            int pageSize, int startCount, String sort
    ) {
        List<MovieSummaryDto> collected = new ArrayList<>();
        int currentStart = startCount;
        int rawTotalCount = 0;
        int skipped = 0;

        for (int attempt = 0; attempt < FILL_PAGE_MAX_ATTEMPTS && collected.size() < pageSize; attempt++) {
            int need = pageSize - collected.size();
            KmdbSearchResponse response = searchByField(field, query, genre, releaseDts, releaseDte, need, currentStart, sort);
            if (attempt == 0) {
                rawTotalCount = response.getTotalCount() == null ? 0 : response.getTotalCount();
            }
            List<KmdbMovieItem> rawItems = response.allItems();
            if (rawItems.isEmpty()) {
                break;
            }

            List<MovieSummaryDto> filtered = toRatedSummaries(rawItems);
            skipped += rawItems.size() - filtered.size();
            collected.addAll(filtered);
            currentStart += rawItems.size();

            if (currentStart >= rawTotalCount) {
                break;
            }
        }

        int totalCount = Math.max(0, rawTotalCount - skipped);
        return new FilledPage(collected, totalCount);
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
                // 제목이 아예 없는 항목(빈 문자열/공백뿐)은 카드에 보여줄 게 없으니 목록에서 뺍니다.
                .filter(movie -> movie.title() != null && !movie.title().isBlank())
                .toList();
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
