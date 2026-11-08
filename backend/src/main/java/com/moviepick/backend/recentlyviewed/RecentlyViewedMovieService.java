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

    // 사용자당 최근 조회 기록은 이 개수만 유지합니다 - 그보다 오래된 기록은 새 영화를 볼 때마다 자동으로
    // 지워집니다(DB가 무한정 쌓이는 것도 막고, "전체보기" 페이지도 적당한 길이로 유지됩니다).
    private static final int MAX_RECENTLY_VIEWED_PER_USER = 100;

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
                            pruneOldest(userId);
                        }
                );
    }

    // 새로 기록될 때만(이미 본 영화는 touch만 하므로 행이 늘지 않음) 호출되므로, 매번 최대 1건만
    // 상한을 넘는지 확인하면 충분합니다.
    private void pruneOldest(Long userId) {
        List<RecentlyViewedMovie> newest = recentlyViewedMovieRepository.findByUserIdOrderByViewedAtDesc(
                userId, PageRequest.of(0, MAX_RECENTLY_VIEWED_PER_USER + 1));
        if (newest.size() > MAX_RECENTLY_VIEWED_PER_USER) {
            recentlyViewedMovieRepository.delete(newest.get(newest.size() - 1));
        }
    }
}
