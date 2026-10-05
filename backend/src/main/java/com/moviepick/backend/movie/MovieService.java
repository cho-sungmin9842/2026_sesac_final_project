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
import com.moviepick.backend.review.TopRatedMovie;
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
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final MovieCacheService movieCacheService;
    private final NowShowingSnapshotRepository nowShowingSnapshotRepository;

    public MovieService(
            KmdbClient kmdbClient,
            MovieMapper movieMapper,
            ReviewService reviewService,
            MovieCacheService movieCacheService,
            NowShowingSnapshotRepository nowShowingSnapshotRepository
    ) {
        this.kmdbClient = kmdbClient;
        this.movieMapper = movieMapper;
        this.reviewService = reviewService;
        this.movieCacheService = movieCacheService;
        this.nowShowingSnapshotRepository = nowShowingSnapshotRepository;
    }

    public MovieSearchResultDto search(String query, List<String> genres, String year, String sort, int page, int pageSize, String field) {
        String releaseDts = year == null || year.isBlank() ? null : year + "0101";
        String releaseDte = year == null || year.isBlank() ? null : year + "1231";
        List<String> cleanGenres = genres == null ? List.of() : genres.stream().filter(g -> g != null && !g.isBlank()).toList();
        boolean hasQuery = query != null && !query.isBlank();
        // 최신순 -> prodYear,1 / 이름순 -> title,0. KMDB가 직접 지원하는 정렬이라 응답 자체가 이미 정렬돼서 옵니다.
        String kmdbSort = toKmdbSort(sort);

        // 평점순(검색어 없음, 장르는 0개~여러 개 모두) - 장르/연도 조건과 함께 KMDB에서 500건만 미리 받아
        // 그 안에서 재정렬하면, 실제로 리뷰가 달린 영화가 그 500건(최신순 상위) 범위 밖에 있는 경우가 흔해
        // 평점 있는 영화가 하나도 안 보이는 문제가 있었습니다(장르를 좁혀도 마찬가지입니다 - 그 장르
        // 안에서도 최신 500건 범위 밖이면 똑같이 안 보입니다). 그래서 평점순일 때는 KMDB 전체를 훑는 대신
        // 우리 DB(리뷰)에 평점이 있는 영화만 먼저 평점순으로 가져온 뒤(이 목록 자체가 작으므로 전수 조사
        // 가능), 장르 조건은 그 안에서 걸러냅니다(평점이 아예 없는 영화는 "평점순" 목록에 넣을 수 없으니 제외).
        if (!hasQuery && "rating".equals(sort)) {
            return searchByRating(cleanGenres, page, pageSize);
        }

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
        return movies.stream().sorted(comparator).toList();
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
            return movieCacheService.findCachedSummariesInOrder(movieIds, ratingsForCache);
        }

        String releaseDte = date.format(KMDB_DATE_FORMAT);
        String releaseDts = date.minusWeeks(4).format(KMDB_DATE_FORMAT);
        // 날짜마다 실제 개봉작 수(KMDB TotalCount)가 다르므로 고정 건수로 자르지 않고, KMDB 한 번 호출 한도
        // (KMDB_MAX_LIST_COUNT)까지 요청해서 그날 "상영중"인 영화 전체를 빠짐없이 받아옵니다.
        FilledPage filled = fetchFilledPage("title", "", null, releaseDts, releaseDte, KMDB_MAX_LIST_COUNT, 0, null);

        List<NowShowingSnapshot> toSave = new ArrayList<>();
        for (int i = 0; i < filled.movies().size(); i++) {
            toSave.add(new NowShowingSnapshot(date, filled.movies().get(i).id(), i));
        }
        nowShowingSnapshotRepository.saveAll(toSave);

        return filled.movies();
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

    // 평점순 전용 - getPopularMovies와 같은 방식(리뷰 테이블에서 먼저 평점순으로 가져온 뒤 KMDB로 채움)
    // 이지만, 고정 limit이 아니라 실제 페이지네이션(page/pageSize)을 지원하고 장르 조건도 받습니다.
    // minScore를 0으로 두면 평점(1~5)이 하나라도 있는 영화는 전부 대상이 됩니다 - 리뷰가 달린 영화 자체가
    // (장르 필터 없이도) 많지 않을 거라 가정하고 전부 가져와 KMDB 상세를 조회한 뒤 장르를 거릅니다.
    private MovieSearchResultDto searchByRating(List<String> genres, int page, int pageSize) {
        List<TopRatedMovie> topRated = reviewService.getTopRatedMovies(0);

        // topRated가 이미 평점 내림차순이라, 장르로 거르기만 해도 그 순서가 그대로 유지됩니다.
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
                matched.add(summary);
            } catch (Exception ignored) {
                // KMDB에서 더 이상 찾을 수 없게 된 movieId는 건너뜁니다.
            }
        }

        int totalCount = matched.size();
        int fromIndex = Math.min((page - 1) * pageSize, totalCount);
        int toIndex = Math.min(page * pageSize, totalCount);
        List<MovieSummaryDto> movies = matched.subList(fromIndex, toIndex);

        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil(totalCount / (double) pageSize);
        return new MovieSearchResultDto(movies, page, pageSize, totalCount, totalPages);
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
        movieCacheService.upsertDetail(detail);
        return detail;
    }
}
