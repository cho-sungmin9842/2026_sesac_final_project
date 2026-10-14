package com.moviepick.backend.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MovieYearIndexRepository extends JpaRepository<MovieYearIndex, MovieYearIndexId> {

    List<MovieYearIndex> findByGenreKeyAndYearKeyOrderByDisplayOrderAsc(String genreKey, String yearKey);

    // 장르를 여러 개 동시에 고른 "전체" 연도 조회(개봉연도=전체)용 - 이 장르의 전체 스캔이 끝나 있으면
    // (MovieCountCacheService.hasCompletedGenreScan) 연도 구분 없이 이 장르에 속한 영화 전체를 KMDB
    // 재호출 없이 바로 가져옵니다.
    List<MovieYearIndex> findByGenreKeyOrderByDisplayOrderAsc(String genreKey);

    @Modifying
    @Transactional
    @Query("delete from MovieYearIndex m where m.genreKey = :genreKey")
    void deleteByGenreKey(@Param("genreKey") String genreKey);
}
