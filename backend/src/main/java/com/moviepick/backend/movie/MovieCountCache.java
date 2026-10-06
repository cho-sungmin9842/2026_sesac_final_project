package com.moviepick.backend.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * "전체 영화" 목록 화면 상단의 건수(예: "53,176건") 캐시. nation이 정확히 "대한민국" 하나뿐인 영화만 센
 * 정확한 개수를 (장르, 개봉연도, 러닝타임) 조합별로 저장해둡니다. KMDB 카탈로그 전체(5만 건 이상)를
 * 끝까지 훑어야 정확한 개수를 알 수 있어 매 요청마다 다시 셀 수 없으므로, 한 번 계산한 뒤 일정 시간
 * (MovieCountCacheService의 CACHE_TTL) 동안 재사용합니다.
 */
@Entity
@Table(name = "movie_count_cache")
@IdClass(MovieCountCacheId.class)
@Getter
public class MovieCountCache {

    // 장르를 고르지 않았을 때("전체")는 "ALL"을 키로 씁니다.
    @Id
    @Column(name = "genre_key", length = 50)
    private String genreKey;

    // 개봉연도를 고르지 않았을 때("전체")도 "ALL"을 키로 씁니다.
    @Id
    @Column(name = "year_key", length = 10)
    private String yearKey;

    // 러닝타임을 고르지 않았을 때("전체")도 "ALL"을 키로 쓰고, 그 외엔 "under120"/"over120"입니다.
    @Id
    @Column(name = "runtime_key", length = 10)
    private String runtimeKey;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    protected MovieCountCache() {
    }

    public MovieCountCache(String genreKey, String yearKey, String runtimeKey, int totalCount, LocalDateTime computedAt) {
        this.genreKey = genreKey;
        this.yearKey = yearKey;
        this.runtimeKey = runtimeKey;
        this.totalCount = totalCount;
        this.computedAt = computedAt;
    }

    public void update(int totalCount, LocalDateTime computedAt) {
        this.totalCount = totalCount;
        this.computedAt = computedAt;
    }
}
