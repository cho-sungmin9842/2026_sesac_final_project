package com.moviepick.backend.movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovieCountCacheRepository extends JpaRepository<MovieCountCache, MovieCountCacheId> {
    Optional<MovieCountCache> findByGenreKeyAndYearKeyAndRuntimeKey(String genreKey, String yearKey, String runtimeKey);
}
