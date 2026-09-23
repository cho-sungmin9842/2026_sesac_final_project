package com.moviepick.backend.watched;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchedMovieRepository extends JpaRepository<WatchedMovie, Long> {

    List<WatchedMovie> findByUserIdOrderByWatchedAtDesc(Long userId);

    boolean existsByUserIdAndMovieId(Long userId, String movieId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);
}
