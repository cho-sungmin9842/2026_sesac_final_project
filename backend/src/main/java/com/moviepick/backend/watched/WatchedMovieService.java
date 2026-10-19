package com.moviepick.backend.watched;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.MovieTitleResolver;
import com.moviepick.backend.watched.dto.WatchedMovieDto;
import com.moviepick.backend.watched.dto.WatchedMovieRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class WatchedMovieService {

    private final WatchedMovieRepository watchedMovieRepository;
    private final UserRepository userRepository;
    private final MovieTitleResolver movieTitleResolver;

    public WatchedMovieService(
            WatchedMovieRepository watchedMovieRepository,
            UserRepository userRepository,
            MovieTitleResolver movieTitleResolver
    ) {
        this.watchedMovieRepository = watchedMovieRepository;
        this.userRepository = userRepository;
        this.movieTitleResolver = movieTitleResolver;
    }

    // 시청완료에 저장된 영화 제목 대신 movies 테이블의 제목으로 보여줍니다(영화 상세/목록/예매 화면과 같은 제목).
    public List<WatchedMovieDto> list(Long userId) {
        List<WatchedMovie> items = watchedMovieRepository.findByUserIdOrderByWatchedAtDesc(userId);
        Map<String, String> titles = movieTitleResolver.titlesById(items.stream().map(WatchedMovie::getMovieId).toList());
        return items.stream()
                .map(item -> WatchedMovieDto.from(item, titles.getOrDefault(item.getMovieId(), item.getMovieTitle())))
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
        // 제목은 프론트가 보낸 값 대신 movies 기준으로 저장합니다(movies에 없는 영화일 때만 요청 값 사용).
        String movieTitle = movieTitleResolver.titleFor(request.movieId(), request.movieTitle());
        WatchedMovie watchedMovie = new WatchedMovie(user, request.movieId(), movieTitle, request.posterUrl());
        return WatchedMovieDto.from(watchedMovieRepository.save(watchedMovie));
    }

    @Transactional
    public void remove(Long userId, String movieId) {
        watchedMovieRepository.deleteByUserIdAndMovieId(userId, movieId);
    }
}
