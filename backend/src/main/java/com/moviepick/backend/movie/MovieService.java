package com.moviepick.backend.movie;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.kmdb.KmdbClient;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbPerson;
import com.moviepick.backend.kmdb.dto.KmdbSearchResponse;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSearchResultDto;
import com.moviepick.backend.movie.dto.MovieSuggestionDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import com.moviepick.backend.review.RatingSummary;
import com.moviepick.backend.review.ReviewService;
import com.moviepick.backend.review.TopRatedMovie;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class MovieService {

    private static final DateTimeFormatter KMDB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // KMDB는 한 번에 최대 약 500건까지만 내려줍니다(1000+는 500 에러) - 정렬을 위해 여러 페이지 분량을 한 번에 받아옵니다.
    private static final int KMDB_MAX_LIST_COUNT = 500;
    // 예매 화면의 "상영중인 영화" 목록(하루 스냅샷)에 담을 최대 편수이자, 그 목록을 KMDB에 요청할 때의 listCount입니다
    // (2026-10-19 요청 - 이전에는 500, 20편으로 줄였다가 같은 날 16편으로 조정). 상영관 3개·정리시간 30분 기준으로
    // 하루 필요 시간이 상영관 용량을 넘지 않게 상영중 영화 수를 제한하는 용도입니다. 시뮬레이션상 16편이면 실제
    // 영화 분포에서 하루의 99.99%가 전부 들어가지만(가장 긴 16편이 매일 상영중인 최악의 경우엔 일요일에 1편이 빠질 수
    // 있음), 15편이면 그 최악의 경우도 전부 들어갑니다.
    // 해외 공동제작/제목 없는 항목이 걸러지면 fetchFilledPage가 이어지는 구간을 추가로 받아 이 편수를 채웁니다.
    static final int NOW_SHOWING_LIST_COUNT = 16;
    // 제목 없는 항목이 껴서 페이지가 모자라면 이어지는 구간을 추가로 더 받아오는데, 그 재시도 최대 횟수입니다
    // (제목 없는 항목은 아주 드물어서 1~2번이면 충분하지만, 여유 있게 잡아둡니다).
    private static final int FILL_PAGE_MAX_ATTEMPTS = 5;

    private final KmdbClient kmdbClient;
    private final MovieMapper movieMapper;
    private final ReviewService reviewService;
    private final MovieCacheService movieCacheService;
    private final NowShowingSnapshotRepository nowShowingSnapshotRepository;
    private final MovieCountCacheService movieCountCacheService;
    private final MovieYearIndexRepository movieYearIndexRepository;
    private final MovieRepository movieRepository;

    public MovieService(
            KmdbClient kmdbClient,
            MovieMapper movieMapper,
            ReviewService reviewService,
            MovieCacheService movieCacheService,
            NowShowingSnapshotRepository nowShowingSnapshotRepository,
            MovieCountCacheService movieCountCacheService,
            MovieYearIndexRepository movieYearIndexRepository,
            MovieRepository movieRepository
    ) {
        this.kmdbClient = kmdbClient;
        this.movieMapper = movieMapper;
        this.reviewService = reviewService;
        this.movieCacheService = movieCacheService;
        this.nowShowingSnapshotRepository = nowShowingSnapshotRepository;
        this.movieCountCacheService = movieCountCacheService;
        this.movieYearIndexRepository = movieYearIndexRepository;
        this.movieRepository = movieRepository;
    }

    // 개봉연도의 "정보 없음" 필터 코드 - movieApi.js가 "정보 없음" 라벨을 이 값으로 바꿔 보냅니다
    // (러닝타임의 "unknown"과 같은 개념). 실제 연도 숫자가 아니므로 KMDB의 releaseDts/Dte로는 절대
    // 걸러낼 수 없어서(날짜 범위 자체를 만들 수 없음), 연도 필터와 똑같이 movie.year() == null로 직접 거릅니다.
    private static final String YEAR_UNKNOWN_FILTER = "unknown";

    public MovieSearchResultDto search(String query, List<String> genres, String year, String runtime, String sort, int page, int pageSize, String field) {
        boolean isUnknownYear = YEAR_UNKNOWN_FILTER.equals(year);
        String releaseDts = year == null || year.isBlank() || isUnknownYear ? null : year + "0101";
        String releaseDte = year == null || year.isBlank() || isUnknownYear ? null : year + "1231";
        List<String> cleanGenres = genres == null ? List.of() : genres.stream().filter(g -> g != null && !g.isBlank()).toList();
        boolean hasQuery = query != null && !query.isBlank();
        // 최신순 -> prodYear,1 / 이름순 -> title,0. KMDB가 직접 지원하는 정렬이라 응답 자체가 이미 정렬돼서 옵니다.
        String kmdbSort = toKmdbSort(sort);

        // 평점순(검색어 없음, 장르는 0개~여러 개 모두) - 장르/연도 조건과 함께 KMDB에서 500건만 미리 받아
        // 그 안에서 재정렬하면, 실제로 리뷰가 달린 영화가 그 500건(최신순 상위) 범위 밖에 있는 경우가 흔해
        // 평점 있는 영화가 하나도 안 보이는 문제가 있었습니다(장르를 좁혀도 마찬가지입니다 - 그 장르
        // 안에서도 최신 500건 범위 밖이면 똑같이 안 보입니다). 그래서 평점순일 때는 리뷰가 있는 영화를
        // 먼저 우리 DB에서 평점 내림차순으로 전수 조사해 맨 앞에 배치하고, 리뷰가 없는 나머지는 뒤에
        // 최신순으로 이어 붙입니다 - "정렬"은 순서만 바꾸는 것이지 "필터"가 아니므로, 전체 건수는 다른
        // 정렬과 동일해야 합니다(2026-10-18 요청 - 예전엔 리뷰 있는 몇 편만 남기고 나머지 수만 건이
        // 통째로 사라져서, 사실상 "평점 있는 영화만 보여주는 필터"처럼 동작했습니다).
        if (!hasQuery && "rating".equals(sort)) {
            return searchByRating(cleanGenres, year, runtime, page, pageSize);
        }

        if (cleanGenres.size() <= 1 && !hasQuery) {
            // 검색어가 없으면(장르/연도만으로 찾아보기, "전체 영화") 뒤에서 텍스트로 한 번 더 거를 필요가 없으니,
            // KMDB의 startCount/listCount로 이번 페이지 분량만 바로 받아옵니다. 그래야 몇 만 건짜리 카탈로그도
            // "마지막 페이지"까지 정확히 이동할 수 있습니다(예전엔 항상 앞쪽 500건만 받아와 그 안에서만 페이지를
            // 나눠서, 500건 밖의 페이지로 가면 빈 목록만 보였습니다).
            String genre = cleanGenres.isEmpty() ? null : cleanGenres.get(0);
            int startCount = (page - 1) * pageSize;
            boolean hasYearFilter = year != null && !year.isBlank();

            List<MovieSummaryDto> pageMovies;
            int fallbackEstimate;
            // 연도 필터가 있고 그 (장르, 연도) 조합의 전체 스캔이 끝나 있으면, KMDB를 다시 부르지 않고
            // movie_year_index + movies 캐시에서 바로 목록을 꺼냅니다(fetchFilledPage의 "날짜 제한 없이
            // 넓게 받아와 직접 거르기" 방식은 2024년처럼 최신순 상위에 몰린 연도가 많으면 탐색 범위 안에
            // 그 연도 항목이 하나도 안 걸려 빈 목록이 나올 수 있었습니다).
            if (hasYearFilter && movieCountCacheService.hasFreshYearIndex(genre, year)) {
                pageMovies = fetchYearIndexedPage(genre, year, runtime, sort, page, pageSize);
                fallbackEstimate = pageMovies.size();
            } else {
                FilledPage filled = fetchFilledPage(field, query, genre, releaseDts, releaseDte, runtime, year, pageSize, startCount, kmdbSort);
                pageMovies = filled.movies();
                fallbackEstimate = filled.totalCount();
            }
            // 화면에 보이는 건수(nation이 정확히 "대한민국"이고 러닝타임 필터까지 통과한 영화만 센 값)가
            // 어긋나지 않도록, 이번 페이지 안에서만 계산한 추정치(fallbackEstimate) 대신 전체 카탈로그를
            // 훑어 캐시해둔 정확한 건수를 씁니다. 아직 캐시가 없으면(첫 조회) 그 추정치를 그대로 보여주고,
            // 정확한 값은 백그라운드에서 계산되는 대로 다음 요청부터 반영됩니다.
            int totalCount = movieCountCacheService.getDomesticTotalCount(genre, year, runtime, fallbackEstimate);
            int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) pageSize);
            return new MovieSearchResultDto(pageMovies, page, pageSize, totalCount, totalPages);
        }

        // 검색어가 있거나 장르를 여러 개 고른 경우는 KMDB 응답을 텍스트로 한 번 더 거르거나 여러 번 합쳐야 해서,
        // 이번 페이지만 딱 집어 받아올 수 없습니다. KMDB 한도(500건) 안에서 최대한 받아와 그 안에서 이번 페이지
        // 구간만 잘라냅니다(따라서 이 경우 페이지 이동은 500건 범위 안에서만 정확합니다).
        int rawListCount = KMDB_MAX_LIST_COUNT;
        List<MovieSummaryDto> allMovies;

        // 개봉연도 필터는 화면에 보이는 연도(개봉일자 우선, 없으면 제작연도)와 같은 기준으로 걸러야 하는데
        // KMDB의 releaseDts/Dte는 개봉일자만 보고 제작연도로의 대체가 없어서(자세한 이유는 fetchFilledPage
        // 주석 참고), 연도 필터가 있을 땐 KMDB에 날짜 제한 없이 요청하고 아래에서 movie.year()로 직접 거릅니다.
        boolean hasYearFilter = year != null && !year.isBlank();
        String effectiveReleaseDts = hasYearFilter ? null : releaseDts;
        String effectiveReleaseDte = hasYearFilter ? null : releaseDte;

        if (cleanGenres.size() > 1) {
            // movie_year_index에 각 장르의 전체 스캔이 이미 끝나 있으면(화면의 "전체 영화 N건" 표시용으로
            // MovieCountCacheService가 이미 한 번씩 훑어둔 색인), KMDB를 장르 개수만큼 또 부르지 않고 그
            // 색인들만 합쳐서 바로 서빙합니다 - 기존 방식(searchByMultipleGenres)은 병렬화해도 장르당
            // 최대 500건씩 KMDB를 다시 불러야 해 몇 초가 걸렸는데, 이 경로는 전부 DB 조회라 체감상 즉시
            // 응답됩니다(2026-10-14 요청 - "이전엔 바로 보였는데 느려짐"). 검색어가 있으면(hasQuery)
            // 색인만으로는 텍스트 일치를 걸러낼 수 없어 이 경로를 쓸 수 없고, 장르 중 하나라도 아직 스캔
            // 전이면 그 장르들의 스캔을 트리거해두고(다음 조회부터는 빨라짐) 이번만 기존 방식으로 응답합니다.
            boolean allGenresScanned = !hasQuery
                    && cleanGenres.stream().allMatch(movieCountCacheService::hasCompletedGenreScan);
            if (allGenresScanned) {
                allMovies = fetchIndexedMultiGenrePage(cleanGenres);
            } else {
                if (!hasQuery) {
                    cleanGenres.forEach(movieCountCacheService::ensureGenreScanTriggered);
                }
                // KMDB genre 파라미터는 한 번에 장르 하나만 받기 때문에(부분일치), 선호 장르가 여러 개(홈
                // 화면 "취향저격 신작 (장르: 코미디, 스릴러, 액션)"처럼)로 필터링할 땐 장르별로 따로 호출한
                // 뒤 합쳐야 합니다.
                allMovies = searchByMultipleGenres(query, cleanGenres, effectiveReleaseDts, effectiveReleaseDte, rawListCount, field, kmdbSort);
            }
        } else {
            String genre = cleanGenres.isEmpty() ? null : cleanGenres.get(0);
            // 검색창의 영화/배우/감독 드롭다운이 고른 필드로, 해외 영화를 뺀 국내 영화만, 정렬까지 맞춰서
            // KMDB에 요청합니다(KmdbClient가 nation=대한민국을 항상 붙입니다).
            KmdbSearchResponse response = searchByField(field, query, genre, effectiveReleaseDts, effectiveReleaseDte, rawListCount, 0, kmdbSort);
            // KMDB는 검색어를 형태소 단위로 쪼개 부분 일치시켜 관련 없는 결과까지 섞어 내려주므로,
            // 실제로 검색어를 포함하는 항목만 한 번 더 걸러냅니다.
            List<KmdbMovieItem> matchedItems = filterByQuery(response.allItems(), field, query);
            allMovies = toRatedSummaries(matchedItems);
        }

        // 러닝타임/개봉연도는 KMDB 검색 파라미터로 정확히 거를 수 없어 응답을 받은 뒤 직접 거릅니다
        // (500건 한도 안에서만 정확).
        allMovies = allMovies.stream()
                .filter(movie -> KmdbTextUtils.matchesRuntimeFilter(movie.runtimeMinutes(), runtime))
                .filter(movie -> matchesYearFilter(movie.year(), year))
                .toList();
        allMovies = sortMovies(allMovies, sort, field, query);

        int fromIndex = Math.min((page - 1) * pageSize, allMovies.size());
        int toIndex = Math.min(page * pageSize, allMovies.size());
        List<MovieSummaryDto> movies = allMovies.subList(fromIndex, toIndex);

        int totalCount = allMovies.size();
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) pageSize);

        return new MovieSearchResultDto(movies, page, pageSize, totalCount, totalPages);
    }

    // 헤더 검색창 자동완성(제목) 전용(5개로 제한 - 프론트가 limit을 넘겨줍니다). KMDB를 부르지 않고
    // 이미 캐시된 movies 테이블만 바로 조회하므로(사실상 전체 카탈로그가 들어있음) 타이핑하는 동안에도
    // 즉시(수십 ms) 응답됩니다.
    public List<MovieSuggestionDto> suggestMovies(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return movieRepository.findTitleSuggestions(query.trim(), limit);
    }

    // 헤더 검색창 자동완성(배우/감독) 전용. "사람 이름"을 자동완성해야 하는데 영화 제목으로 답하면
    // 안 되므로(2026-10-18 실측 - 배우/감독으로 검색해도 영화 제목이 후보로 떴던 버그), 영화가 아니라
    // 그 영화들에 실제로 달려 있는 배우/감독 "이름"만 뽑아 중복을 제거하고 돌려줍니다. 한 번의 영화
    // 검색 응답 안에 이름이 일치하는 사람이 여러 명 섞여 나올 수 있어 limit보다 넉넉히(4배) 받아
    // 그 안에서 고릅니다. 키워드(줄거리/소재)는 '사람 이름'이라는 개념 자체가 없어 이 메서드를 쓰지
    // 않고, 자동완성 없이 일반 입력으로만 검색합니다(2026-10-18 요청).
    public List<String> suggestPersonNames(String query, String field, int limit) {
        if (query == null || query.isBlank() || !("actor".equals(field) || "director".equals(field))) {
            return List.of();
        }
        String trimmed = query.trim();
        KmdbSearchResponse response = searchByField(field, trimmed, null, null, null, limit * 4, 0, null);

        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (KmdbMovieItem item : response.allItems()) {
            List<KmdbPerson> persons = "actor".equals(field)
                    ? (item.getActors() == null ? null : item.getActors().getActor())
                    : (item.getDirectors() == null ? null : item.getDirectors().getDirector());
            if (persons == null) {
                continue;
            }
            for (KmdbPerson person : persons) {
                String name = person.displayName();
                if (name != null && containsIgnoreCase(name, trimmed)) {
                    names.add(name);
                }
            }
        }

        return names.stream()
                .sorted(Comparator
                        .comparingInt((String name) -> name.equals(trimmed) ? 0 : name.startsWith(trimmed) ? 1 : 2)
                        .thenComparingInt(String::length))
                .limit(limit)
                .toList();
    }

    // fetchYearIndexedPage와 같은 movie_year_index를 쓰지만, 연도를 하나로 좁히지 않고 장르별 전체
    // 목록(모든 연도)을 가져와 합칩니다 - 개봉연도=전체인 다중 장르 조회용입니다. 러닝타임/연도 필터와
    // 정렬은 이 메서드가 하지 않고, search()의 공용 후처리(matchesRuntimeFilter/matchesYearFilter/
    // sortMovies)가 다른 경로들과 똑같이 처리합니다 - 필터링 로직을 두 곳에 중복시키지 않기 위함입니다.
    private List<MovieSummaryDto> fetchIndexedMultiGenrePage(List<String> genres) {
        LinkedHashSet<String> movieIds = new LinkedHashSet<>();
        for (String genre : genres) {
            for (MovieYearIndex row : movieYearIndexRepository.findByGenreKeyOrderByDisplayOrderAsc(genre)) {
                movieIds.add(row.getMovieId());
            }
        }
        List<String> idList = new ArrayList<>(movieIds);

        Map<String, RatingSummary> ratings = reviewService.getRatingSummaries(idList);
        Map<String, MovieCacheService.MovieSummaryRating> ratingsForCache = ratings.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, e -> new MovieCacheService.MovieSummaryRating(e.getValue().averageScore(), (int) e.getValue().reviewCount())));
        return movieCacheService.findCachedSummariesInOrder(idList, ratingsForCache);
    }

    // 장르를 여러 개(예: 범죄+스포츠) 고르면 KMDB genre 파라미터가 한 번에 하나만 받아서 장르 개수만큼
    // 호출해야 하는데, 예전엔 이걸 순서대로(for문) 불러서 장르 개수에 거의 비례해 응답 시간이 늘어났습니다
    // (2026-10-14 실측 - 장르 2개 선택 시 눈에 띄게 느려짐, 직전 턴에서 다중 장르 필터가 정상 동작하도록
    // 고치면서 체감되기 시작함). KMDB 호출은 서로 독립적이라 CompletableFuture로 동시에 보내 가장 느린
    // 호출 하나만큼의 시간으로 줄입니다. 다만 toRatedSummaries는 영화 캐시 테이블에 읽고 쓰는 작업이라,
    // 같은 영화가 두 장르에 걸쳐 있으면(흔함) 두 스레드가 동시에 같은 행을 upsert하다 충돌할 수 있어
    // - KMDB 응답을 모으고 중복만 제거한 뒤 메인 스레드에서 딱 한 번만 호출합니다.
    private List<MovieSummaryDto> searchByMultipleGenres(
            String query, List<String> genres, String releaseDts, String releaseDte, int rawListCount, String field, String kmdbSort
    ) {
        List<CompletableFuture<List<KmdbMovieItem>>> futures = genres.stream()
                .map(genre -> CompletableFuture.supplyAsync(() -> {
                    KmdbSearchResponse response = searchByField(field, query, genre, releaseDts, releaseDte, rawListCount, 0, kmdbSort);
                    return filterByQuery(response.allItems(), field, query);
                }))
                .toList();

        Map<String, KmdbMovieItem> mergedItems = new LinkedHashMap<>();
        for (CompletableFuture<List<KmdbMovieItem>> future : futures) {
            for (KmdbMovieItem item : future.join()) {
                mergedItems.putIfAbsent(movieMapper.toId(item), item);
            }
        }
        return toRatedSummaries(new ArrayList<>(mergedItems.values()));
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

    // KMDB의 실제 표기(예: "퍼펙트 게임")와 사용자가 입력하는 표기(예: "퍼펙트게임") 사이에 띄어쓰기
    // 차이만 있는 경우가 흔해서, 비교 시에는 양쪽 모두 공백을 없앤 뒤 부분 일치를 검사합니다.
    private boolean containsIgnoreCase(String value, String needle) {
        String cleaned = KmdbTextUtils.clean(value);
        if (cleaned == null) {
            return false;
        }
        String normalizedValue = cleaned.toLowerCase(Locale.KOREAN).replaceAll("\\s+", "");
        String normalizedNeedle = needle.toLowerCase(Locale.KOREAN).replaceAll("\\s+", "");
        return normalizedValue.contains(normalizedNeedle);
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
        return sortMovies(movies, sort, null, null);
    }

    // field/query가 있으면(검색창에 제목을 입력한 경우) 사용자가 입력한 제목과 완전히 같은 영화를 먼저
    // 보여주고, 그 다음에 제목에 포함만 된(부분일치) 영화를 보여줍니다 - 지금까지는 "포함"으로 걸러낸
    // 결과를 그대로(최신순/이름순 등으로만) 섞어서 보여줘, 완전히 같은 제목의 영화가 목록 중간/뒤쪽에
    // 묻히는 문제가 있었습니다(2026-10-14 실측). 장르/연도만으로 찾아볼 때(query 없음)나 배우/감독/키워드
    // 검색에는 "제목이 완전히 같다"는 개념 자체가 없으므로 field가 title(또는 미지정=기본값)이고 query가
    // 있을 때만 적용합니다. 같은 그룹(완전일치 vs 부분일치) 안에서는 기존 정렬 기준을 그대로 씁니다.
    private List<MovieSummaryDto> sortMovies(List<MovieSummaryDto> movies, String sort, String field, String query) {
        Comparator<MovieSummaryDto> comparator;
        if ("name".equals(sort)) {
            // title이 빈 값이라 null인 항목도 있어서(KmdbTextUtils.clean), Collator 비교 전에 null을 뒤로 뺍니다.
            comparator = Comparator.comparing(MovieSummaryDto::title, Comparator.nullsLast(Collator.getInstance(Locale.KOREAN)));
        } else if ("rating".equals(sort)) {
            // 리뷰가 하나도 없는 영화(averageScore null)는 평점이 없는 거라 항상 맨 뒤로 보내고, 리뷰가
            // 있는 영화끼리는 평점 높은 순으로 정렬합니다. reverseOrder를 nullsLast 안쪽에 넣어야
            // null은 그대로 맨 뒤에 고정되고, null이 아닌 값들만 내림차순이 됩니다(바깥에서 전체를
            // reversed()하면 null까지 맨 앞으로 와버립니다).
            comparator = Comparator.comparing(MovieSummaryDto::averageScore, Comparator.nullsLast(Comparator.reverseOrder()));
        } else {
            comparator = Comparator.comparing((MovieSummaryDto movie) -> movie.year() == null ? Integer.MIN_VALUE : movie.year())
                    .reversed();
        }
        boolean titleSearch = query != null && !query.isBlank() && (field == null || "title".equals(field));
        if (titleSearch) {
            comparator = Comparator.comparing((MovieSummaryDto movie) -> isExactTitle(movie, query) ? 0 : 1)
                    .thenComparing(comparator);
        }
        return movies.stream().sorted(comparator).toList();
    }

    // 공백 차이만 있어도 같은 제목으로 보는 기준은 containsIgnoreCase와 동일하게 맞춥니다("퍼펙트 게임"
    // 입력 시 "퍼펙트게임"도 완전일치로 쳐야 하므로, 공백 제거 후 비교).
    private boolean isExactTitle(MovieSummaryDto movie, String query) {
        String title = KmdbTextUtils.clean(movie.title());
        String needle = KmdbTextUtils.clean(query);
        if (title == null || needle == null) {
            return false;
        }
        return title.toLowerCase(Locale.KOREAN).replaceAll("\\s+", "")
                .equals(needle.toLowerCase(Locale.KOREAN).replaceAll("\\s+", ""));
    }

    // 예매 화면의 "상영중인 영화" 목록 - 실제 상영 스케줄 API가 없어서, 최근 4주 내 개봉일자(releaseDts~releaseDte)로
    // 대신합니다. 접속 날짜 기준으로 하루에 한 번만 계산해 DB에 스냅샷으로 저장해두고, 같은 날 재방문 시에는
    // KMDB를 다시 부르지 않고 이 스냅샷(과 movies 캐시)에서 그대로 읽습니다.
    public MovieSearchResultDto getNowShowing(int listCount) {
        List<MovieSummaryDto> movies = getOrCreateTodayShowingMovies();
        int totalCount = movies.size();
        List<MovieSummaryDto> limited = movies.size() > listCount ? movies.subList(0, listCount) : movies;
        return new MovieSearchResultDto(limited, 1, listCount, totalCount, totalCount == 0 ? 0 : 1);
    }

    // getNowShowing()과 상영정보 자동 생성 배치(ScreeningGenerationService)가 공통으로 쓰는, 오늘 날짜 기준
    // "상영중" 영화 전체 목록입니다. 오늘 스냅샷이 이미 있으면 그대로 재사용하고, 없으면(그날 첫 조회) KMDB로
    // 계산해서 스냅샷에 저장합니다 - 같은 날에는 어느 쪽에서 먼저 호출하든 KMDB 호출이 하루 한 번으로 줄어듭니다.
    public List<MovieSummaryDto> getOrCreateTodayShowingMovies() {
        return getOrCreateShowingMoviesForDate(LocalDate.now());
    }

    // 위와 같은 로직을 임의의 날짜 기준으로 계산합니다. ScreeningGenerationService가 한 주(월~일) 상영정보를
    // 만들 때, "월요일 기준 상영중 목록" 하나로 7일치를 전부 채우면 화~일요일 사이에 상영중 목록이 바뀐(예:
    // 새로 개봉했거나 개봉 4주가 지나 빠진) 영화가 그 주 내내 상영정보 없이 누락되는 문제가 있어, 요일별로
    // 각각의 날짜 기준 상영중 목록을 따로 계산해 써야 합니다.
    public List<MovieSummaryDto> getOrCreateShowingMoviesForDate(LocalDate date) {
        List<NowShowingSnapshot> snapshot = nowShowingSnapshotRepository.findBySnapshotDateOrderByDisplayOrderAsc(date);

        if (!snapshot.isEmpty()) {
            List<String> movieIds = snapshot.stream().map(NowShowingSnapshot::getMovieId).toList();
            Map<String, RatingSummary> ratings = reviewService.getRatingSummaries(movieIds);
            Map<String, MovieCacheService.MovieSummaryRating> ratingsForCache = ratings.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> new MovieCacheService.MovieSummaryRating(entry.getValue().averageScore(), (int) entry.getValue().reviewCount())
                    ));
            // display_order는 스냅샷을 처음 저장할 때(KMDB가 내려준 순서)의 순번이라 제목순이 아닙니다.
            // 예매 탭/관리자 화면 둘 다 제목 오름차순으로 보여줘야 해서 읽을 때마다 다시 정렬합니다.
            return sortMovies(movieCacheService.findCachedSummariesInOrder(movieIds, ratingsForCache), "name");
        }

        String releaseDte = date.format(KMDB_DATE_FORMAT);
        String releaseDts = date.minusWeeks(4).format(KMDB_DATE_FORMAT);
        // 상영관 용량을 넘지 않게 하루 최대 NOW_SHOWING_LIST_COUNT(16)편만 받아옵니다. 최근 4주 내 개봉작이 그보다
        // 많은 날은 KMDB가 돌려주는 앞쪽 16편만 "상영중"으로 칩니다(정렬 파라미터를 안 보내므로 어떤 16편인지는 KMDB 순서).
        FilledPage filled = fetchFilledPage("title", "", null, releaseDts, releaseDte, null, null, NOW_SHOWING_LIST_COUNT, 0, null);
        List<MovieSummaryDto> sorted = sortMovies(filled.movies(), "name");

        List<NowShowingSnapshot> toSave = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            toSave.add(new NowShowingSnapshot(date, sorted.get(i).id(), i));
        }
        nowShowingSnapshotRepository.saveAll(toSave);

        return sorted;
    }

    private record FilledPage(List<MovieSummaryDto> movies, int totalCount) {
    }

    // 최신순 정렬 상위권에 러닝타임이 짧은 단편/독립영화가 몰려있어서, "2시간 이상" 필터처럼 통과율이 아주
    // 낮은(실측 약 2~3%) 조건은 pageSize만큼만 청해서는 FILL_PAGE_MAX_ATTEMPTS 안에 한 건도 못 채우고
    // 빈 페이지가 나올 수 있습니다. 러닝타임 필터가 걸려 있을 땐 시도당 요청 건수를 넉넉히 올려(최대 KMDB
    // 한도까지) 같은 시도 횟수 안에서 더 많은 원본 데이터를 훑도록 합니다.
    private static final int RUNTIME_FILTERED_FETCH_SIZE = 300;

    // KMDB의 startCount/listCount로 이번 페이지 구간을 받아온 뒤 제목 없는 항목, 해외 공동제작 작품
    // (isDomesticOnly가 걸러내는, nation이 "대한민국" 단독이 아닌 항목), 러닝타임/개봉연도 필터에 안 맞는
    // 항목이 걸러져서 pageSize보다 모자라면, 이어지는 구간을 추가로 더 받아와 정확히 pageSize개를 채웁니다.
    // totalCount는 KMDB가 알려주는 전체 건수에서 "이번 페이지를 채우는 동안" 걸러낸 개수만큼을 뺀 값입니다 -
    // 카탈로그 전체에서 걸러지는 항목이 정확히 몇 건인지는 몇 만 건을 전부 훑어야 알 수 있어 매 요청마다
    // 계산하지 않으므로, 다른 페이지에서는 이 보정이 반영되지 않을 수 있습니다(화면 상단 "N건"은 이 값
    // 대신 MovieCountCacheService가 전체 카탈로그를 훑어 캐시해둔 정확한 값을 씁니다).
    private FilledPage fetchFilledPage(
            String field, String query, String genre, String releaseDts, String releaseDte, String runtime,
            String year, int pageSize, int startCount, String sort
    ) {
        List<MovieSummaryDto> collected = new ArrayList<>();
        int currentStart = startCount;
        int rawTotalCount = 0;
        int skipped = 0;
        boolean hasRuntimeFilter = runtime != null && !runtime.isBlank();
        boolean hasYearFilter = year != null && !year.isBlank();
        // 개봉연도 필터는 화면에 보이는 연도(개봉일자 우선, 없으면 제작연도 - MovieMapper.toYear 참고)와
        // 똑같은 기준으로 걸러야 합니다. KMDB의 releaseDts/Dte는 개봉일자만 보고 제작연도로의 대체가 없어서,
        // 이 파라미터를 그대로 걸면 제작연도로만 표시되는(개봉일자가 없는) 영화가 전부 빠집니다(실측:
        // 연도별로 걸러보면 전체의 82%가 누락 - MovieCountCacheService 주석 참고). 그래서 연도 필터가
        // 있을 땐 KMDB에는 날짜 제한 없이 요청하고, 응답을 받은 뒤 movie.year()로 직접 거릅니다.
        String effectiveReleaseDts = hasYearFilter ? null : releaseDts;
        String effectiveReleaseDte = hasYearFilter ? null : releaseDte;

        for (int attempt = 0; attempt < FILL_PAGE_MAX_ATTEMPTS && collected.size() < pageSize; attempt++) {
            int need = (hasRuntimeFilter || hasYearFilter)
                    ? RUNTIME_FILTERED_FETCH_SIZE
                    : pageSize - collected.size();
            KmdbSearchResponse response = searchByField(field, query, genre, effectiveReleaseDts, effectiveReleaseDte, need, currentStart, sort);
            if (attempt == 0) {
                rawTotalCount = response.getTotalCount() == null ? 0 : response.getTotalCount();
            }
            List<KmdbMovieItem> rawItems = response.allItems();
            if (rawItems.isEmpty()) {
                break;
            }

            List<MovieSummaryDto> filtered = toRatedSummaries(rawItems).stream()
                    .filter(movie -> KmdbTextUtils.matchesRuntimeFilter(movie.runtimeMinutes(), runtime))
                    .filter(movie -> matchesYearFilter(movie.year(), year))
                    .toList();
            skipped += rawItems.size() - filtered.size();
            collected.addAll(filtered);
            currentStart += rawItems.size();

            if (currentStart >= rawTotalCount) {
                break;
            }
        }

        int totalCount = Math.max(0, rawTotalCount - skipped);
        // 러닝타임/개봉연도 필터 때는 시도당 넉넉히 받아오다 보니 한 번에 pageSize보다 많이 모일 수 있어 잘라냅니다.
        List<MovieSummaryDto> page = collected.size() > pageSize ? collected.subList(0, pageSize) : collected;
        return new FilledPage(page, totalCount);
    }

    private boolean matchesYearFilter(Integer movieYear, String yearFilter) {
        if (yearFilter == null || yearFilter.isBlank()) {
            return true;
        }
        if (YEAR_UNKNOWN_FILTER.equals(yearFilter)) {
            return movieYear == null;
        }
        return movieYear != null && String.valueOf(movieYear).equals(yearFilter);
    }

    // movie_year_index(MovieCountCacheService의 전체 스캔이 함께 채워둔 색인)에서 이 (장르, 연도)
    // 조합의 movie_id 순서를 그대로 가져와 movies 캐시에서 요약 정보를 꺼내 붙입니다 - KMDB를 다시
    // 호출하지 않으므로 연도 하나당 수천 건이 있어도 전부 DB 조회만으로 끝납니다. 러닝타임 필터와
    // 정렬(이름순/최신순)은 색인 순서만으로는 못 맞추니 이 자리에서 직접 거르고 다시 정렬합니다.
    private List<MovieSummaryDto> fetchYearIndexedPage(String genre, String year, String runtime, String sort, int page, int pageSize) {
        String genreKey = genre == null || genre.isBlank() ? "ALL" : genre;
        List<String> movieIds = movieYearIndexRepository.findByGenreKeyAndYearKeyOrderByDisplayOrderAsc(genreKey, year).stream()
                .map(MovieYearIndex::getMovieId)
                .toList();
        if (movieIds.isEmpty()) {
            return List.of();
        }

        Map<String, RatingSummary> ratings = reviewService.getRatingSummaries(movieIds);
        Map<String, MovieCacheService.MovieSummaryRating> ratingsForCache = ratings.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new MovieCacheService.MovieSummaryRating(e.getValue().averageScore(), (int) e.getValue().reviewCount())));

        List<MovieSummaryDto> movies = movieCacheService.findCachedSummariesInOrder(movieIds, ratingsForCache).stream()
                .filter(movie -> KmdbTextUtils.matchesRuntimeFilter(movie.runtimeMinutes(), runtime))
                .toList();
        movies = sortMovies(movies, sort);

        int from = Math.min((page - 1) * pageSize, movies.size());
        int to = Math.min(from + pageSize, movies.size());
        return movies.subList(from, to);
    }

    private List<MovieSummaryDto> toRatedSummaries(List<KmdbMovieItem> items) {
        items = items.stream().filter(item -> KmdbTextUtils.isDomesticOnlyNation(item.getNation())).toList();
        List<String> movieIds = items.stream().map(movieMapper::toId).toList();
        Map<String, RatingSummary> ratings = reviewService.getRatingSummaries(movieIds);

        List<MovieSummaryDto> summaries = items.stream()
                .map(item -> {
                    RatingSummary rating = ratings.get(movieMapper.toId(item));
                    return rating == null
                            ? movieMapper.toSummary(item)
                            : movieMapper.toSummary(item, rating.averageScore(), (int) rating.reviewCount());
                })
                // 제목이 아예 없는 항목(빈 문자열/공백뿐)은 카드에 보여줄 게 없으니 목록에서 뺍니다.
                .filter(movie -> movie.title() != null && !movie.title().isBlank())
                .toList();

        // KMDB에서 받아온 목록 데이터를 movies 캐시에 누적합니다(다음부터는 이 영화들의 상세/재검색이 DB로 서빙됨).
        movieCacheService.upsertSummaries(summaries);
        return summaries;
    }

    // 홈 화면 "지금 인기 있는 영화" - 고정된 영화 제목 목록이 아니라 실제 리뷰 평균 평점이 minScore 이상인 영화를 평점순으로 보여줍니다.
    public List<MovieSummaryDto> getPopularMovies(double minScore, int limit) {
        List<TopRatedMovie> topRated = reviewService.getTopRatedMovies(minScore);
        List<MovieSummaryDto> results = new ArrayList<>();

        for (TopRatedMovie rated : topRated) {
            if (results.size() >= limit) {
                break;
            }
            String[] parts = rated.movieId().split("_", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                MovieSummaryDto summary = summaryForId(rated.movieId(), parts[0], parts[1], rated.averageScore(), (int) rated.reviewCount());
                if (summary != null && summary.title() != null && !summary.title().isBlank()) {
                    results.add(summary);
                }
            } catch (Exception ignored) {
                // KMDB에서 더 이상 찾을 수 없게 된 movieId는 건너뜁니다.
            }
        }
        return results;
    }

    // 평점순 - 리뷰가 있는 영화를 평점 내림차순으로 먼저 전수 조사해 앞쪽에 배치하고(실제로 몇 안 되므로
    // 전부 가져와도 됩니다), 리뷰가 없는 나머지는 최신순 카탈로그에서 이어 붙입니다. "정렬"일 뿐 "필터"가
    // 아니므로, 전체 건수는 다른 정렬(최신순/이름순)과 같아야 합니다(2026-10-18 요청 - 예전엔 리뷰 있는
    // 몇 편만 남기고 나머지가 통째로 사라졌습니다).
    private MovieSearchResultDto searchByRating(List<String> genres, String year, String runtime, int page, int pageSize) {
        List<MovieSummaryDto> rated = ratedMoviesMatching(genres, year, runtime);
        Set<String> ratedIds = rated.stream().map(MovieSummaryDto::id).collect(Collectors.toSet());

        int fromIndex = (page - 1) * pageSize;
        int toIndex = page * pageSize;
        List<MovieSummaryDto> pageMovies = new ArrayList<>();
        int ratedFrom = Math.min(fromIndex, rated.size());
        int ratedTo = Math.min(toIndex, rated.size());
        if (ratedFrom < ratedTo) {
            pageMovies.addAll(rated.subList(ratedFrom, ratedTo));
        }

        int remaining = pageSize - pageMovies.size();
        int unratedSkip = Math.max(0, fromIndex - rated.size());
        int totalCount;

        if (genres.size() <= 1) {
            // 장르 0~1개는 '최신순'과 같은 전체 건수 캐시를 그대로 씁니다 - 리뷰 있는 영화도 결국 이
            // 전체 건수 안에 포함된 숫자라서, 평점순이라고 전체 건수 자체가 달라지지 않습니다.
            String genre = genres.isEmpty() ? null : genres.get(0);
            if (remaining > 0) {
                pageMovies.addAll(fetchLatestFillerSingleGenre(genre, year, runtime, unratedSkip, remaining, ratedIds));
            }
            totalCount = movieCountCacheService.getDomesticTotalCount(genre, year, runtime, rated.size());
        } else {
            // 장르 2개 이상은 이미 색인으로 정확한 전체 목록을 바로 모을 수 있으므로, 거기서 리뷰 있는
            // 영화를 뺀 나머지를 그대로 이어 붙이고 실제 개수로 전체 건수를 셉니다.
            List<MovieSummaryDto> unratedForGenres = fetchIndexedMultiGenrePage(genres).stream()
                    .filter(movie -> KmdbTextUtils.matchesRuntimeFilter(movie.runtimeMinutes(), runtime))
                    .filter(movie -> matchesYearFilter(movie.year(), year))
                    .filter(movie -> !ratedIds.contains(movie.id()))
                    .toList();
            if (remaining > 0) {
                int from = Math.min(unratedSkip, unratedForGenres.size());
                int to = Math.min(unratedSkip + remaining, unratedForGenres.size());
                pageMovies.addAll(unratedForGenres.subList(from, to));
            }
            totalCount = rated.size() + unratedForGenres.size();
        }

        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) pageSize);
        return new MovieSearchResultDto(pageMovies, page, pageSize, totalCount, totalPages);
    }

    // 리뷰가 있는 영화를 평점 내림차순으로 전수 조사합니다(실제로 몇 안 되므로 KMDB 상세까지 전부
    // 조회해도 됩니다) - searchByRating이 "평점순" 맨 앞에 배치할 때 씁니다.
    private List<MovieSummaryDto> ratedMoviesMatching(List<String> genres, String year, String runtime) {
        List<TopRatedMovie> topRated = reviewService.getTopRatedMovies(0);
        List<MovieSummaryDto> matched = new ArrayList<>();
        for (TopRatedMovie rated : topRated) {
            String[] parts = rated.movieId().split("_", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                MovieSummaryDto summary = summaryForId(rated.movieId(), parts[0], parts[1], rated.averageScore(), (int) rated.reviewCount());
                if (summary == null || summary.title() == null || summary.title().isBlank()) {
                    continue;
                }
                // 장르를 하나도 안 골랐으면("전체") 거르지 않고, 골랐으면 그중 하나라도 겹치면 포함합니다
                // (홈 화면 "취향저격 신작"의 다중 장르 필터와 같은 OR 방식).
                if (!genres.isEmpty() && genres.stream().noneMatch(genre -> summary.genres().contains(genre))) {
                    continue;
                }
                if (!KmdbTextUtils.matchesRuntimeFilter(summary.runtimeMinutes(), runtime)) {
                    continue;
                }
                if (!matchesYearFilter(summary.year(), year)) {
                    continue;
                }
                matched.add(summary);
            } catch (Exception ignored) {
                // KMDB에서 더 이상 찾을 수 없게 된 movieId는 건너뜁니다.
            }
        }
        return matched;
    }

    // 평점순 뒷부분(리뷰 없는 나머지)을 최신순 카탈로그에서 채웁니다. rated로 이미 앞쪽에 배치한 영화는
    // 최신순 안에서도 자기 자리를 그대로 가지고 있으므로, 중복으로 다시 나오지 않도록 걸러내면서 필요한
    // 개수(count)만큼 채울 때까지 몇 번 더 받아옵니다(제외되는 건수가 보통 몇 건 안 되므로 금방 채워집니다).
    private List<MovieSummaryDto> fetchLatestFillerSingleGenre(
            String genre, String year, String runtime, int skip, int count, Set<String> excludeIds
    ) {
        List<MovieSummaryDto> result = new ArrayList<>();
        int fetchStart = skip;
        for (int attempt = 0; attempt < 5 && result.size() < count; attempt++) {
            int need = count - result.size() + excludeIds.size();
            FilledPage filled = fetchFilledPage(null, "", genre, null, null, runtime, year, need, fetchStart, "prodYear,1");
            if (filled.movies().isEmpty()) {
                break;
            }
            filled.movies().stream().filter(movie -> !excludeIds.contains(movie.id())).forEach(result::add);
            fetchStart += filled.movies().size();
            if (filled.movies().size() < need) {
                break; // 카탈로그 끝에 도달 - 더 받아와도 늘어나지 않습니다.
            }
        }
        return result.size() > count ? result.subList(0, count) : result;
    }

    // 요약 정보는 DB 캐시를 먼저 확인하고, 없을 때만 KMDB를 호출해 새로 캐시에 저장합니다.
    private MovieSummaryDto summaryForId(String compositeId, String movieId, String movieSeq, Double averageScore, int reviewCount) {
        return movieCacheService.findCachedSummary(compositeId, averageScore, reviewCount)
                .orElseGet(() -> {
                    KmdbMovieItem item = kmdbClient.findByMovieId(movieId, movieSeq).allItems().stream().findFirst().orElse(null);
                    if (item == null) {
                        return null;
                    }
                    MovieSummaryDto summary = movieMapper.toSummary(item, averageScore, reviewCount);
                    // 제목이 특수문자뿐이라 cleanTitle이 null을 돌려준 영화는 movies 테이블의 title이
                    // NOT NULL이라 그대로 캐시에 저장할 수 없으니, 캐시하지 않고 그냥 null을 돌려줍니다
                    // (호출 쪽이 이미 title 빈 값을 건너뛰도록 되어있습니다).
                    if (summary.title() == null || summary.title().isBlank()) {
                        return null;
                    }
                    movieCacheService.upsertSummaries(List.of(summary));
                    return summary;
                });
    }

    public MovieDetailDto getDetail(String movieId, String movieSeq) {
        String compositeId = movieId + "_" + movieSeq;
        RatingSummary rating = reviewService.getRatingSummaries(List.of(compositeId)).get(compositeId);
        Double averageScore = rating == null ? null : rating.averageScore();
        int reviewCount = rating == null ? 0 : (int) rating.reviewCount();

        // DB에 상세까지 캐시된 영화는 KMDB를 아예 부르지 않고 그대로 서빙합니다.
        Optional<MovieDetailDto> cached = movieCacheService.findCachedDetail(compositeId, averageScore, reviewCount);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<KmdbMovieItem> items = kmdbClient.findByMovieId(movieId, movieSeq).allItems();
        KmdbMovieItem item = items.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException("영화를 찾을 수 없습니다: " + compositeId, HttpStatus.NOT_FOUND));

        MovieDetailDto detail = movieMapper.toDetail(item, averageScore, reviewCount);
        // 제목이 특수문자뿐이라 cleanTitle이 null을 돌려준 영화는 movies 테이블의 title이 NOT NULL이라
        // 캐시에 저장할 수 없고, 화면에 보여줄 제목도 없으니 "찾을 수 없음"으로 처리합니다.
        if (detail.title() == null || detail.title().isBlank()) {
            throw new ApiException("영화를 찾을 수 없습니다: " + compositeId, HttpStatus.NOT_FOUND);
        }
        movieCacheService.upsertDetail(detail);
        return detail;
    }
}
