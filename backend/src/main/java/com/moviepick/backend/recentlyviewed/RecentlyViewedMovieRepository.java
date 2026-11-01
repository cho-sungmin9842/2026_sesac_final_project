package com.moviepick.backend.recentlyviewed;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecentlyViewedMovieRepository extends JpaRepository<RecentlyViewedMovie, Long> {

    List<RecentlyViewedMovie> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);

    Optional<RecentlyViewedMovie> findByUserIdAndMovieId(Long userId, String movieId);
}
