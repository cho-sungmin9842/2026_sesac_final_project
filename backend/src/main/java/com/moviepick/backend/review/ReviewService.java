package com.moviepick.backend.review;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.MovieTitleResolver;
import com.moviepick.backend.review.dto.ReviewDto;
import com.moviepick.backend.review.dto.ReviewRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final MovieTitleResolver movieTitleResolver;

    public ReviewService(
            ReviewRepository reviewRepository, UserRepository userRepository, MovieTitleResolver movieTitleResolver
    ) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.movieTitleResolver = movieTitleResolver;
    }

    public List<ReviewDto> list(String movieId) {
        return toDtos(reviewRepository.findByMovieIdOrderByCreatedAtDesc(movieId));
    }

    public List<ReviewDto> listByUser(Long userId) {
        return toDtos(reviewRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId));
    }

    // 리뷰에 저장된 영화 제목 대신 movies 테이블의 제목으로 보여줍니다(영화 상세/목록/예매 화면과 같은 제목).
    private List<ReviewDto> toDtos(List<Review> reviews) {
        Map<String, String> titles = movieTitleResolver.titlesById(
                reviews.stream().map(Review::getMovieId).toList());
        return reviews.stream()
                .map(review -> ReviewDto.from(review, titles.getOrDefault(review.getMovieId(), review.getMovieTitle())))
                .toList();
    }

    // 관리자 "리뷰 신고 관리" 화면 - DB에 저장된 모든 사용자의 리뷰를 전부 조회합니다.
    public List<ReviewDto> listAllForAdmin(Long adminUserId) {
        requireAdmin(adminUserId);
        return toDtos(reviewRepository.findAllByOrderByCreatedAtDesc());
    }

    // 관리자는 작성자가 아니어도 부적절한 리뷰를 삭제할 수 있어야 하므로, 본인 리뷰로만 제한하는
    // requireOwnedReview()와 별도로 둡니다.
    public void adminDelete(Long reviewId, Long adminUserId) {
        requireAdmin(adminUserId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException("리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        reviewRepository.delete(review);
    }

    public ReviewDto create(String movieId, Long userId, ReviewRequest request) {
        // 프론트의 "리뷰 쓰기" 버튼은 이미 리뷰를 쓴 영화면 비활성화하지만, AI 채팅처럼 그 버튼을 거치지
        // 않는 경로로는 여전히 중복 작성이 가능했습니다(2026-10-11 실측) - 같은 규칙을 여기서도 강제합니다.
        if (reviewRepository.existsByUserIdAndMovieId(userId, movieId)) {
            throw new ApiException("이미 작성한 리뷰가 있습니다. 기존 리뷰를 수정해주세요.", HttpStatus.CONFLICT);
        }
        User user = requireUser(userId);
        // 제목은 프론트가 보낸 값 대신 movies 기준으로 저장합니다(movies에 없는 영화일 때만 요청 값 사용).
        String movieTitle = movieTitleResolver.titleFor(movieId, request.movieTitle());
        Review review = new Review(movieId, movieTitle, user, request.score(), request.content());
        return ReviewDto.from(reviewRepository.save(review));
    }

    public ReviewDto update(String movieId, Long reviewId, Long userId, ReviewRequest request) {
        Review review = requireOwnedReview(movieId, reviewId, userId);
        review.update(request.score(), request.content());
        Review saved = reviewRepository.save(review);
        return ReviewDto.from(saved, movieTitleResolver.titleFor(saved.getMovieId(), saved.getMovieTitle()));
    }

    public void delete(String movieId, Long reviewId, Long userId) {
        Review review = requireOwnedReview(movieId, reviewId, userId);
        reviewRepository.delete(review);
    }

    // 영화 목록/검색 결과에 붙일 평균 평점을 movieId별로 한 번에 모아서 돌려줍니다(N+1 방지).
    public Map<String, RatingSummary> getRatingSummaries(Collection<String> movieIds) {
        if (movieIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.aggregateRatings(movieIds).stream()
                .collect(Collectors.toMap(
                        ReviewRepository.MovieRatingRow::getMovieId,
                        row -> new RatingSummary(row.getAverageScore(), row.getReviewCount())
                ));
    }

    // 홈 화면 "지금 인기 있는 영화" - 평균 평점이 minScore 이상인 영화를 평점 높은 순으로 돌려줍니다(고정 제목 목록 없음).
    public List<TopRatedMovie> getTopRatedMovies(double minScore) {
        return reviewRepository.findTopRated(minScore).stream()
                .map(row -> new TopRatedMovie(row.getMovieId(), row.getAverageScore(), row.getReviewCount()))
                .toList();
    }

    private Review requireOwnedReview(String movieId, Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(candidate -> candidate.getMovieId().equals(movieId))
                .orElseThrow(() -> new ApiException("리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!review.getUser().getId().equals(userId)) {
            throw new ApiException("본인이 작성한 리뷰만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        return review;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
    }

    private void requireAdmin(Long userId) {
        if (!requireUser(userId).isAdmin()) {
            throw new ApiException("관리자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
