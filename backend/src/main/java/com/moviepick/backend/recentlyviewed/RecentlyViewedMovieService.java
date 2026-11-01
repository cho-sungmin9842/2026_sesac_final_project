package com.moviepick.backend.recentlyviewed;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.MovieTitleResolver;
import com.moviepick.backend.recentlyviewed.dto.RecentlyViewedMovieDto;
import com.moviepick.backend.recentlyviewed.dto.RecentlyViewedMovieRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class RecentlyViewedMovieService {

    private final RecentlyViewedMovieRepository recentlyViewedMovieRepository;
    private final UserRepository userRepository;
    private final MovieTitleResolver movieTitleResolver;

    public RecentlyViewedMovieService(
            RecentlyViewedMovieRepository recentlyViewedMovieRepository,
            UserRepository userRepository,
            MovieTitleResolver movieTitleResolver
    ) {
        this.recentlyViewedMovieRepository = recentlyViewedMovieRepository;
        this.userRepository = userRepository;
        this.movieTitleResolver = movieTitleResolver;
    }

    // 홈 화면 "최근 본 영화" 섹션 - 최근에 조회한 순서대로 최대 limit개.
    public List<RecentlyViewedMovieDto> list(Long userId, int limit) {
        List<RecentlyViewedMovie> items =
                recentlyViewedMovieRepository.findByUserIdOrderByViewedAtDesc(userId, PageRequest.of(0, limit));
        Map<String, String> titles =
                movieTitleResolver.titlesById(items.stream().map(RecentlyViewedMovie::getMovieId).toList());
        return items.stream()
                .map(item -> RecentlyViewedMovieDto.from(item, titles.getOrDefault(item.getMovieId(), item.getMovieTitle())))
                .toList();
    }

    // 영화 상세 페이지를 열 때마다 호출 - 이미 본 영화면 시간만 갱신하고, 처음이면 새로 기록합니다(1인당 1행).
    @Transactional
    public void record(Long userId, RecentlyViewedMovieRequest request) {
        String movieTitle = movieTitleResolver.titleFor(request.movieId(), request.movieTitle());
        recentlyViewedMovieRepository.findByUserIdAndMovieId(userId, request.movieId())
                .ifPresentOrElse(
                        existing -> existing.touch(movieTitle, request.posterUrl()),
                        () -> {
                            User user = userRepository.findById(userId)
                                    .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
                            recentlyViewedMovieRepository.save(
                                    new RecentlyViewedMovie(user, request.movieId(), movieTitle, request.posterUrl()));
                        }
                );
    }
}
