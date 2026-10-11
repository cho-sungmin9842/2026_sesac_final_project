package com.moviepick.backend.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MovieYearIndexRepository extends JpaRepository<MovieYearIndex, MovieYearIndexId> {

    List<MovieYearIndex> findByGenreKeyAndYearKeyOrderByDisplayOrderAsc(String genreKey, String yearKey);

    @Modifying
    @Transactional
    @Query("delete from MovieYearIndex m where m.genreKey = :genreKey")
    void deleteByGenreKey(@Param("genreKey") String genreKey);
}
