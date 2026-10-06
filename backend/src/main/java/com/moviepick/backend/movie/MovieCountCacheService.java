package com.moviepick.backend.movie;

import com.moviepick.backend.kmdb.KmdbClient;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbSearchResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * "전체 영화" 목록의 "N건" 표시용 - 화면에 보이는 개수(nation이 정확히 "대한민국"인 영화만, 러닝타임
 * 필터까지 반영한 값)와 실제로 끝까지 페이지를 넘겼을 때의 건수가 어긋나지 않도록, (장르, 개봉연도,
 * 러닝타임) 조합별 정확한 총 건수를 계산해 캐시해둡니다.
 * <p>
 * KMDB는 nation 조건에 맞는 "전체" 건수만 알려줄 뿐 그중 몇 건이 해외 공동제작인지, 러닝타임이 얼마인지는
 * 알려주지 않으므로, 정확한 수를 구하려면 그 장르/연도 조합의 전체 카탈로그를 끝까지 훑어야 합니다. 장르/연도
 * 조건이 없는 "전체" 조합은 5만 건이 넘어 KMDB를 100번 넘게 호출해야 하고(실측 결과 수 분~십수 분
 * 걸릴 수 있습니다), 이걸 사용자가 페이지를 기다리는 HTTP 요청 안에서 그대로 하면 화면이 그 시간
 * 내내 "불러오는 중"으로 멈춰버립니다. 그래서 계산은 별도 스레드에서 백그라운드로 돌리고, 계산이
 * 끝나기 전까지는 캐시된 값(있으면 오래된 값이라도)이나 호출 쪽이 넘겨준 추정치를 즉시 돌려줍니다 -
 * 계산이 끝나면 다음 요청부터는 정확한 값이 나옵니다. 러닝타임 3종(전체/2시간 미만/2시간 이상)은 어차피
 * 카탈로그를 한 번 훑는 김에 함께 집계되므로, (장르, 연도) 조합 하나당 스캔은 한 번만 돕니다.
 */
@Slf4j
@Service
public class MovieCountCacheService {

    private static final String ALL_KEY = "ALL";
    private static final String RUNTIME_UNDER_KEY = "under120";
    private static final String RUNTIME_OVER_KEY = "over120";
    // KMDB 카탈로그는 거의 매일 조금씩 늘어나지만, 매번 백그라운드 재계산을 돌리는 건 낭비라 이 정도
    // 유효시간 동안은 캐시된 값을 그대로 보여줍니다.
    private static final long CACHE_TTL_HOURS = 6;
    // KMDB는 한 번에 최대 약 500건까지만 내려줍니다.
    private static final int SCAN_PAGE_SIZE = 500;

    private final KmdbClient kmdbClient;
    private final MovieCountCacheRepository repository;
    // 같은 (장르, 연도) 조합의 백그라운드 재계산이 동시에 여러 번 겹쳐 돌지 않도록 막는 용도입니다.
    private final ConcurrentHashMap<String, Boolean> scanning = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public MovieCountCacheService(KmdbClient kmdbClient, MovieCountCacheRepository repository) {
        this.kmdbClient = kmdbClient;
        this.repository = repository;
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
    // "under120"/"over120"입니다. fallbackEstimate는 아직 정확한 값을 계산해둔 적이 없을 때(캐시가 전혀
    // 없을 때) 대신 보여줄 값으로, 호출 쪽(MovieService)이 이미 가지고 있는 "이번 페이지 안에서만 추정한" 값을
    // 그대로 넘겨받습니다.
    public int getDomesticTotalCount(String genre, String year, String runtimeFilter, int fallbackEstimate) {
        String genreKey = normalizeKey(genre);
        String yearKey = normalizeKey(year);
        String runtimeKey = normalizeRuntimeKey(runtimeFilter);

        Optional<MovieCountCache> cached = repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, yearKey, runtimeKey);
        boolean fresh = cached.isPresent()
                && cached.get().getComputedAt().isAfter(LocalDateTime.now().minusHours(CACHE_TTL_HOURS));
        if (fresh) {
            return cached.get().getTotalCount();
        }

        triggerBackgroundScan(genre, year, genreKey, yearKey);
        return cached.map(MovieCountCache::getTotalCount).orElse(fallbackEstimate);
    }

    private String normalizeKey(String value) {
        return value == null || value.isBlank() ? ALL_KEY : value;
    }

    private String normalizeRuntimeKey(String runtimeFilter) {
        if (runtimeFilter == null || runtimeFilter.isBlank()) {
            return ALL_KEY;
        }
        return RUNTIME_UNDER_KEY.equals(runtimeFilter) ? RUNTIME_UNDER_KEY : RUNTIME_OVER_KEY;
    }

    private void triggerBackgroundScan(String genre, String year, String genreKey, String yearKey) {
        String scanKey = genreKey + "::" + yearKey;
        if (scanning.putIfAbsent(scanKey, Boolean.TRUE) != null) {
            return;
        }
        executor.submit(() -> {
            try {
                Map<String, Integer> counts = scanDomesticTotalCounts(genre, year);
                LocalDateTime now = LocalDateTime.now();
                counts.forEach((runtimeKey, total) -> {
                    MovieCountCache row = repository.findByGenreKeyAndYearKeyAndRuntimeKey(genreKey, yearKey, runtimeKey)
                            .map(existing -> {
                                existing.update(total, now);
                                return existing;
                            })
                            .orElseGet(() -> new MovieCountCache(genreKey, yearKey, runtimeKey, total, now));
                    repository.save(row);
                });
            } catch (Exception e) {
                log.error("영화 건수 캐시 계산 중 오류 (genre={}, year={})", genre, year, e);
            } finally {
                scanning.remove(scanKey);
            }
        });
    }

    // (장르, 연도) 조합의 카탈로그를 한 번 훑으면서 "전체"/"2시간 미만"/"2시간 이상" 세 건수를 동시에 셉니다.
    private Map<String, Integer> scanDomesticTotalCounts(String genre, String year) {
        String releaseDts = year == null || year.isBlank() ? null : year + "0101";
        String releaseDte = year == null || year.isBlank() ? null : year + "1231";

        int allCount = 0;
        int underCount = 0;
        int overCount = 0;
        int startCount = 0;
        int rawTotalCount = Integer.MAX_VALUE;

        while (startCount < rawTotalCount) {
            KmdbSearchResponse response = kmdbClient.searchByTitle("", genre, releaseDts, releaseDte, SCAN_PAGE_SIZE, startCount, null);
            if (startCount == 0) {
                rawTotalCount = response.getTotalCount() == null ? 0 : response.getTotalCount();
            }
            List<KmdbMovieItem> items = response.allItems();
            if (items.isEmpty()) {
                break;
            }
            for (KmdbMovieItem item : items) {
                String title = KmdbTextUtils.cleanTitle(item.getTitle());
                if (title == null || title.isBlank() || !KmdbTextUtils.isDomesticOnlyNation(item.getNation())) {
                    continue;
                }
                allCount++;
                Integer runtimeMinutes = KmdbTextUtils.parseRuntimeMinutes(item.getRuntime());
                if (runtimeMinutes == null) {
                    continue;
                }
                if (runtimeMinutes < KmdbTextUtils.RUNTIME_FILTER_THRESHOLD_MINUTES) {
                    underCount++;
                } else {
                    overCount++;
                }
            }
            startCount += items.size();
        }
        return Map.of(ALL_KEY, allCount, RUNTIME_UNDER_KEY, underCount, RUNTIME_OVER_KEY, overCount);
    }
}
