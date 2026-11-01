package com.moviepick.backend.movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovieCountCacheRepository extends JpaRepository<MovieCountCache, MovieCountCacheId> {
    Optional<MovieCountCache> findByGenreKeyAndYearKeyAndRuntimeKeyAndAgeRatingKey(
            String genreKey, String yearKey, String runtimeKey, String ageRatingKey
    );
}
