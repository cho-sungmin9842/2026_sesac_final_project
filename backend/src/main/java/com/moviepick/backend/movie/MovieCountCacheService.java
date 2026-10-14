package com.moviepick.backend.movie;

import com.moviepick.backend.kmdb.KmdbClient;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbSearchResponse;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * "전체 영화" 목록의 "N건" 표시용 - 화면에 보이는 개수(nation이 정확히 "대한민국"인 영화만, 개봉연도/러닝타임
 * 필터까지 반영한 값)와 실제로 끝까지 페이지를 넘겼을 때의 건수가 어긋나지 않도록, (장르, 개봉연도, 러닝타임)
 * 조합별 정확한 총 건수를 계산해 캐시해둡니다.
 * <p>
 * 개봉연도는 KMDB의 releaseDts/releaseDte(개봉일자) 파라미터로는 정확히 셀 수 없습니다 - 화면에 보이는
 * "연도"는 개봉일자가 없으면 제작연도(prodYear)로 대신 표시하는데(MovieMapper.toYear 참고), KMDB 검색
 * 파라미터는 개봉일자만 볼 뿐 제작연도로의 대체가 없어서, releaseDts/Dte로 연도별로 따로따로 스캔하면
 * 제작연도로만 표시되는(개봉일자가 없는) 영화들이 전부 빠집니다(2026-10-10 실측: 연도별 합계 9,269건 vs
 * 실제 전체 52,467건 - 82%가 누락). 그래서 연도는 KMDB에 날짜 제한 없이 장르 하나만 넘겨 카탈로그를 한 번
 * 훑으면서, 화면과 똑같은 기준(MovieMapper.toYear)으로 모든 연도 구간을 동시에 집계합니다 - 예전처럼 연도
 * 하나당 스캔을 따로 돌리지 않고 장르 하나당 스캔을 "딱 한 번만" 돌려서 모든 (연도, 러닝타임) 조합을
 * 한꺼번에 채우므로, 오히려 예전보다 KMDB 호출이 줄어듭니다.
 * <p>
 * 장르/연도 조건이 없는 "전체" 조합은 5만 건이 넘어 KMDB를 100번 넘게 호출해야 하고(실측 결과 수 분~십수 분
 * 걸릴 수 있습니다), 이걸 사용자가 페이지를 기다리는 HTTP 요청 안에서 그대로 하면 화면이 그 시간
 * 내내 "불러오는 중"으로 멈춰버립니다. 그래서 계산은 별도 스레드에서 백그라운드로 돌리고, 계산이
 * 끝나기 전까지는 캐시된 값(있으면 오래된 값이라도)이나 호출 쪽이 넘겨준 추정치를 즉시 돌려줍니다 -
 * 계산이 끝나면 다음 요청부터는 정확한 값이 나옵니다.
 */
@Slf4j
@Service
public class MovieCountCacheService {

    private static final String ALL_KEY = "ALL";
    private static final String RUNTIME_UNDER_KEY = "under120";
    private static final String RUNTIME_OVER_KEY = "over120";
    private static final String RUNTIME_UNKNOWN_KEY = "unknown";
    // 개봉일자도 제작연도도 둘 다 없어 연도 자체를 알 수 없는 영화 전용 연도 키 - MovieService의
    // YEAR_UNKNOWN_FILTER와 같은 값이어야 합니다(화면의 "정보 없음").
    private static final String YEAR_UNKNOWN_KEY = "unknown";
    // KMDB 카탈로그는 거의 매일 조금씩 늘어나지만, 매번 백그라운드 재계산을 돌리는 건 낭비라 이 정도
    // 유효시간 동안은 캐시된 값을 그대로 보여줍니다.
    private static final long CACHE_TTL_HOURS = 6;
    // KMDB는 한 번에 최대 약 500건까지만 내려줍니다.
    private static final int SCAN_PAGE_SIZE = 500;

    // movie_year_index 저장은 한 번에 너무 큰 배치로 묶지 않도록 끊어서 저장합니다.
    private static final int INDEX_SAVE_BATCH_SIZE = 1000;

    private final KmdbClient kmdbClient;
    private final MovieMapper movieMapper;
    private final MovieCountCacheRepository repository;
    private final MovieCacheService movieCacheService;
    private final MovieYearIndexRepository movieYearIndexRepository;
    // 같은 장르의 백그라운드 재계산이 동시에 여러 번 겹쳐 돌지 않도록 막는 용도입니다(이제 연도는 스캔
    // 자체를 제한하지 않으므로 장르 하나당 스캔도 하나뿐입니다).
    private final ConcurrentHashMap<String, Boolean> scanning = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public MovieCountCacheService(
            KmdbClient kmdbClient,
            MovieMapper movieMapper,
            MovieCountCacheRepository repository,
            MovieCacheService movieCacheService,
            MovieYearIndexRepository movieYearIndexRepository
    ) {
        this.kmdbClient = kmdbClient;
        this.movieMapper = movieMapper;
        this.repository = repository;
        this.movieCacheService = movieCacheService;
        this.movieYearIndexRepository = movieYearIndexRepository;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // genre/year는 "전체"일 때 null(또는 빈 값)로 들어옵니다. runtimeFilter는 null/빈 값("전체") 또는
    // "under120"/"over120"/"unknown"입니다. fallbackEstimate는 아직 정확한 값을 계산해둔 적이 없을 때
    // (캐시가 전혀 없을 때) 대신 보여줄 값으로, 호출 쪽(MovieService)이 이미 가지고 있는 "이번 페이지
    // 안에서만 추정한" 값을 그대로 넘겨받습니다.
    public int getDomesticTotalCount(String genre, String year, String runtimeFilter, int fallbackEstimate) {
        String genreKey = normalizeKey(genre);
        String yearKey = normalizeKey(year);
        String runtimeKey = normalizeRuntimeKey(runtimeFilter);

        Optional<MovieCountCache> cached = repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, yearKey, runtimeKey);
        if (cached.isPresent()
                && cached.get().getComputedAt().isAfter(LocalDateTime.now().minusHours(CACHE_TTL_HOURS))) {
            return cached.get().getTotalCount();
        }

        // 이 (연도, 러닝타임) 조합 캐시가 없거나 오래됐어도, "장르 전체" 스캔 결과((genre, ALL, ALL) 행)
        // 자체가 아직 최신이면 재스캔을 또 트리거하지 않습니다 - 1919년 이전처럼 영화가 한 편도 없는 연도는
        // scanDomesticTotalCounts가 영원히 그 조합의 행을 안 만들어서(발견한 조합만 저장하므로) "이 조합만
        // 캐시가 없다"가 늘 참이 되고, 예전엔 그때마다 매번 장르 전체 재스캔(몇 분~십수 분 소요, KMDB
        // 수십~백여 회 호출)을 새로 띄워서, 그 재스캔의 "일단 색인을 통째로 지우고 다시 채우기" 단계
        // 때문에 재스캔이 끝날 때까지 그 장르의 모든 연도 목록이 통째로 빈 화면이 되는 문제가 있었습니다
        // (2026-10-13, 오래된 연도를 여러 번 조회해봤더니 재발). 장르 전체 스캔 자체가 오래됐을 때만
        // 재스캔을 띄우고, 이 조합 하나만 없는 경우엔 트리거하지 않습니다.
        Optional<MovieCountCache> genreScan = repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, ALL_KEY, ALL_KEY);
        boolean genreScanFresh = genreScan.isPresent()
                && genreScan.get().getComputedAt().isAfter(LocalDateTime.now().minusHours(CACHE_TTL_HOURS));
        if (!genreScanFresh) {
            triggerBackgroundScan(genre, genreKey);
        }

        if (cached.isPresent()) {
            return cached.get().getTotalCount();
        }

        // 이 조합의 캐시 행이 아예 없을 때, 장르 자체는 이미 최소 한 번 전체 스캔이 끝나 있다면(= (genre,
        // ALL, ALL) 행이 존재) "아직 못 구한 값"이 아니라 "스캔 중 이 조합에 해당하는 영화를 하나도 못
        // 찾았다"는 뜻입니다(scanDomesticTotalCounts는 발견한 모든 조합을 빠짐없이 저장하므로, 빠졌다는 건
        // 0건이 확정입니다). 그 경우에도 fallbackEstimate(이번 페이지의 KMDB 원본 TotalCount - 연도/국내
        // 필터를 반영하기 전 값이라 수만 건으로 잡힘)를 그대로 보여주면 "건수는 5만 건인데 목록은 0건"처럼
        // 보여서 혼란스러웠습니다. 장르를 아예 스캔한 적이 없을 때만 fallbackEstimate를 보여줍니다.
        return genreScan.isPresent() ? 0 : fallbackEstimate;
    }

    // movie_year_index에서 실제 목록을 바로 꺼내 써도 되는지(=이 (장르, 연도) 조합의 전체 스캔이
    // 최신 상태인지) 확인합니다. getDomesticTotalCount와 달리 여기서는 스캔을 새로 트리거하지 않습니다 -
    // 호출하는 쪽(MovieService.search)이 totalCount를 구하려고 getDomesticTotalCount를 항상 같이
    // 부르므로, 트리거는 그쪽에서 이미 일어납니다. runtimeKey는 "ALL"로 고정해서 확인합니다 - 스캔은
    // 러닝타임과 무관하게 장르 하나당 한 번만 돌고, 그 안에서 모든 러닝타임 구간을 한꺼번에 채우기
    // 때문입니다(러닝타임 필터는 색인에서 꺼낸 뒤 MovieService가 직접 거릅니다).
    // getDomesticTotalCount와 마찬가지로, 스캔된 지 오래된(stale) 색인이라도 "데이터 자체가 있으면"
    // 그대로 씁니다(freshness는 백그라운드 재스캔을 트리거할지에만 쓰이고, 그 트리거는 이미 같이 호출되는
    // getDomesticTotalCount 쪽에서 일어납니다). 예전엔 여기서만 freshness를 엄격히 요구해서, 6시간
    // TTL이 지나자마자 멀쩡한 색인 데이터를 두고도 fetchFilledPage의 "최신순 상위 1500건만 훑는" 폴백으로
    // 떨어졌습니다 - 그 폴백은 "정보 없음"(연도 미상) 같은 연도 필터를 사실상 영영 못 찾아서(그 1500건
    // 안에 개봉연도 미상 영화가 섞여 있을 가능성이 극히 낮음), 집계 건수(1,765건)는 그대로인데 실제 목록은
    // 0건으로 나오는 불일치가 있었습니다(2026-10-13).
    public boolean hasFreshYearIndex(String genre, String year) {
        if (year == null || year.isBlank()) {
            return false;
        }
        String genreKey = normalizeKey(genre);
        return repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, year, ALL_KEY).isPresent();
    }

    // (genre, ALL, ALL) 행이 있으면 이 장르는 전체 카탈로그 스캔이 최소 한 번 끝난 상태라, movie_year_index
    // 에서 이 장르의 전체 목록을 KMDB 재호출 없이 바로 꺼내 써도 됩니다(다중 장르 검색을 빠르게 하는 용도 -
    // 2026-10-14 요청). hasFreshYearIndex와 달리 연도 파라미터가 필요 없고(장르 전체 스캔 여부만 봄),
    // staleness는 신경 쓰지 않습니다(오래된 색인이라도 데이터 자체가 있으면 그대로 씁니다 - 최신화는
    // ensureGenreScanTriggered가 별도로 트리거하는 백그라운드 재스캔이 끝나는 대로 반영됩니다).
    public boolean hasCompletedGenreScan(String genre) {
        String genreKey = normalizeKey(genre);
        return repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, ALL_KEY, ALL_KEY).isPresent();
    }

    // 다중 장르 조회에서 hasCompletedGenreScan이 false인 장르를 발견했을 때 호출합니다 - 이번 요청은
    // 기존 KMDB 폴백으로 응답하되, 이 장르의 스캔을 미리 걸어둬서 다음 조회부터는 빨라지게 합니다.
    public void ensureGenreScanTriggered(String genre) {
        String genreKey = normalizeKey(genre);
        Optional<MovieCountCache> genreScan = repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, ALL_KEY, ALL_KEY);
        boolean genreScanFresh = genreScan.isPresent()
                && genreScan.get().getComputedAt().isAfter(LocalDateTime.now().minusHours(CACHE_TTL_HOURS));
        if (!genreScanFresh) {
            triggerBackgroundScan(genre, genreKey);
        }
    }

    private String normalizeKey(String value) {
        return value == null || value.isBlank() ? ALL_KEY : value;
    }

    private String normalizeRuntimeKey(String runtimeFilter) {
        if (runtimeFilter == null || runtimeFilter.isBlank()) {
            return ALL_KEY;
        }
        if (RUNTIME_UNDER_KEY.equals(runtimeFilter)) {
            return RUNTIME_UNDER_KEY;
        }
        return RUNTIME_UNKNOWN_KEY.equals(runtimeFilter) ? RUNTIME_UNKNOWN_KEY : RUNTIME_OVER_KEY;
    }

    // 연도는 더 이상 스캔 범위를 제한하지 않으므로(날짜 제한 없이 장르 전체를 훑어 모든 연도를 한 번에
    // 집계), scanKey는 장르만으로 충분합니다.
    private void triggerBackgroundScan(String genre, String genreKey) {
        if (scanning.putIfAbsent(genreKey, Boolean.TRUE) != null) {
            return;
        }
        executor.submit(() -> {
            try {
                ScanResult result = scanDomesticTotalCounts(genre);
                LocalDateTime now = LocalDateTime.now();
                result.countsByYear().forEach((yearKey, runtimeCounts) ->
                        runtimeCounts.forEach((runtimeKey, total) -> {
                            MovieCountCache row = repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, yearKey, runtimeKey)
                                    .map(existing -> {
                                        existing.update(total, now);
                                        return existing;
                                    })
                                    .orElseGet(() -> new MovieCountCache(genreKey, yearKey, runtimeKey, total, now));
                            repository.save(row);
                        })
                );

                // 연도 필터용 색인은 이번 스캔 결과로 통째로 갈아끼웁니다(사이에 빠진 영화가 생기지 않도록
                // 지우기 전에 새 데이터를 먼저 다 모아둔 뒤, 여기서 한꺼번에 지우고 다시 씁니다).
                movieYearIndexRepository.deleteByGenreKey(genreKey);
                for (int i = 0; i < result.indexRows().size(); i += INDEX_SAVE_BATCH_SIZE) {
                    int end = Math.min(i + INDEX_SAVE_BATCH_SIZE, result.indexRows().size());
                    movieYearIndexRepository.saveAll(result.indexRows().subList(i, end));
                }
            } catch (Exception e) {
                log.error("영화 건수 캐시 계산 중 오류 (genre={})", genre, e);
            } finally {
                scanning.remove(genreKey);
            }
        });
    }

    private record ScanResult(Map<String, Map<String, Integer>> countsByYear, List<MovieYearIndex> indexRows) {
    }

    // 이 장르의 카탈로그를 날짜 제한 없이 한 번 훑으면서, 영화마다 화면에 보이는 것과 똑같은 기준
    // (MovieMapper.toYear - 개봉일자 우선, 없으면 제작연도)으로 연도를 구해 (연도 x 러닝타임) 건수를
    // 전부 동시에 집계합니다. "ALL" 연도 키는 이 장르의 연도 무관 합계이고, 그 외 키는 "2024"처럼 실제
    // 연도 문자열이거나(연도를 아예 알 수 없는 극소수 영화는) "unknown"입니다(러닝타임의 "정보 없음"과
    // 같은 개념 - 화면의 개봉연도 필터 맨 끝 "정보 없음" 항목).
    // 같은 자리에서 movie_year_index에 넣을 (연도, movie_id) 순서도 함께 모으고, movies 캐시에도
    // 요약 정보를 페이지 단위로 누적해서, 연도 필터로 실제 목록을 요청할 때 KMDB를 다시 부르지 않고
    // 이 둘만으로 바로 서빙할 수 있게 합니다.
    private ScanResult scanDomesticTotalCounts(String genre) {
        Map<String, Map<String, Integer>> countsByYear = new HashMap<>();
        List<MovieYearIndex> indexRows = new ArrayList<>();
        String genreKey = normalizeKey(genre);
        int startCount = 0;
        int rawTotalCount = Integer.MAX_VALUE;
        int displayOrder = 0;

        // 기본 화면 정렬(최신순)과 색인 순서가 같도록 스캔도 같은 정렬(prodYear desc)로 받아옵니다.
        while (startCount < rawTotalCount) {
            KmdbSearchResponse response = kmdbClient.searchByTitle("", genre, null, null, SCAN_PAGE_SIZE, startCount, "prodYear,1");
            if (startCount == 0) {
                rawTotalCount = response.getTotalCount() == null ? 0 : response.getTotalCount();
            }
            List<KmdbMovieItem> items = response.allItems();
            if (items.isEmpty()) {
                break;
            }
            List<MovieSummaryDto> pageSummaries = new ArrayList<>();
            for (KmdbMovieItem item : items) {
                String title = KmdbTextUtils.cleanTitle(item.getTitle());
                if (title == null || title.isBlank() || !KmdbTextUtils.isDomesticOnlyNation(item.getNation())) {
                    continue;
                }
                String runtimeKey = runtimeKeyFor(item);
                tally(countsByYear, ALL_KEY, runtimeKey);
                pageSummaries.add(movieMapper.toSummary(item));

                Integer year = movieMapper.toYear(item);
                String yearKey = year != null ? String.valueOf(year) : YEAR_UNKNOWN_KEY;
                tally(countsByYear, yearKey, runtimeKey);
                indexRows.add(new MovieYearIndex(genreKey, yearKey, movieMapper.toId(item), displayOrder++));
            }
            // 평점 조회 없이(스캔 전용) 요약 필드만 movies 캐시에 쌓아, 색인에서 꺼내 쓸 때 KMDB를
            // 다시 호출할 필요가 없게 합니다. 평점은 서빙 시점에 ReviewService에서 따로 붙입니다.
            movieCacheService.upsertSummaries(pageSummaries);
            startCount += items.size();
        }
        return new ScanResult(countsByYear, indexRows);
    }

    private String runtimeKeyFor(KmdbMovieItem item) {
        Integer runtimeMinutes = KmdbTextUtils.parseRuntimeMinutes(item.getRuntime());
        if (runtimeMinutes == null) {
            return RUNTIME_UNKNOWN_KEY;
        }
        return runtimeMinutes < KmdbTextUtils.RUNTIME_FILTER_THRESHOLD_MINUTES ? RUNTIME_UNDER_KEY : RUNTIME_OVER_KEY;
    }

    // yearKey(ALL 또는 특정 연도)에 해당 영화 1건을 "그 연도 전체"와 "그 연도 + 이 러닝타임 구간" 양쪽에 더합니다.
    private void tally(Map<String, Map<String, Integer>> countsByYear, String yearKey, String runtimeKey) {
        Map<String, Integer> runtimeCounts = countsByYear.computeIfAbsent(yearKey, k -> new HashMap<>());
        runtimeCounts.merge(ALL_KEY, 1, Integer::sum);
        runtimeCounts.merge(runtimeKey, 1, Integer::sum);
    }
}
