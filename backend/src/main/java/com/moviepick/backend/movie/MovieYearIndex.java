package com.moviepick.backend.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 개봉연도 필터 전용 색인. MovieCountCacheService가 건수를 집계하는 전체 스캔과 같은 자리에서 함께
 * 채워집니다 - KMDB의 releaseDts/Dte로는 "화면에 보이는 연도"(개봉일자 없으면 제작연도 대체) 기준으로
 * 정확히 거를 수 없어서(MovieCountCacheService 주석 참고), 연도 필터가 걸렸을 때 실제 목록을 KMDB를
 * 다시 호출해 거르는 대신 이 색인에서 바로 꺼내 씁니다. display_order는 스캔 당시 KMDB 정렬(최신순)
 * 순서를 그대로 보존합니다.
 */
@Entity
@Table(name = "movie_year_index")
@IdClass(MovieYearIndexId.class)
@Getter
public class MovieYearIndex {

    @Id
    @Column(name = "genre_key", length = 50)
    private String genreKey;

    @Id
    @Column(name = "year_key", length = 10)
    private String yearKey;

    @Id
    @Column(name = "movie_id", length = 64)
    private String movieId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected MovieYearIndex() {
    }

    public MovieYearIndex(String genreKey, String yearKey, String movieId, int displayOrder) {
        this.genreKey = genreKey;
        this.yearKey = yearKey;
        this.movieId = movieId;
        this.displayOrder = displayOrder;
    }
}
