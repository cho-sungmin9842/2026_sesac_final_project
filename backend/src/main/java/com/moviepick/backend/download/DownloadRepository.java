package com.moviepick.backend.download;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DownloadRepository extends JpaRepository<Download, Long> {

    List<Download> findByUserIdOrderByDownloadedAtDesc(Long userId);

    Optional<Download> findByUserIdAndMovieId(Long userId, String movieId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);
}
