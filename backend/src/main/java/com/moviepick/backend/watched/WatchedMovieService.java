package com.moviepick.backend.watched;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.watched.dto.WatchedMovieDto;
import com.moviepick.backend.watched.dto.WatchedMovieRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WatchedMovieService {

    private final WatchedMovieRepository watchedMovieRepository;
    private final UserRepository userRepository;

    public WatchedMovieService(WatchedMovieRepository watchedMovieRepository, UserRepository userRepository) {
        this.watchedMovieRepository = watchedMovieRepository;
        this.userRepository = userRepository;
    }

    public List<WatchedMovieDto> list(Long userId) {
        return watchedMovieRepository.findByUserIdOrderByWatchedAtDesc(userId).stream()
                .map(WatchedMovieDto::from)
                .toList();
    }

    public boolean isWatched(Long userId, String movieId) {
        return watchedMovieRepository.existsByUserIdAndMovieId(userId, movieId);
    }

    public WatchedMovieDto add(Long userId, WatchedMovieRequest request) {
        if (watchedMovieRepository.existsByUserIdAndMovieId(userId, request.movieId())) {
            return list(userId).stream()
                    .filter(dto -> dto.movieId().equals(request.movieId()))
                    .findFirst()
                    .orElseThrow();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
        WatchedMovie watchedMovie = new WatchedMovie(user, request.movieId(), request.movieTitle(), request.posterUrl());
        return WatchedMovieDto.from(watchedMovieRepository.save(watchedMovie));
    }

    @Transactional
    public void remove(Long userId, String movieId) {
        watchedMovieRepository.deleteByUserIdAndMovieId(userId, movieId);
    }
}
