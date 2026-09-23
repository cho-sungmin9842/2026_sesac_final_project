package com.moviepick.backend.review;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
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

    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    public List<ReviewDto> list(String movieId) {
        return reviewRepository.findByMovieIdOrderByCreatedAtDesc(movieId).stream()
                .map(ReviewDto::from)
                .toList();
    }

    public List<ReviewDto> listByUser(Long userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ReviewDto::from)
                .toList();
    }

    public ReviewDto create(String movieId, Long userId, ReviewRequest request) {
        User user = requireUser(userId);
        Review review = new Review(movieId, request.movieTitle(), user, request.score(), request.content());
        return ReviewDto.from(reviewRepository.save(review));
    }

    public ReviewDto update(String movieId, Long reviewId, Long userId, ReviewRequest request) {
        Review review = requireOwnedReview(movieId, reviewId, userId);
        review.update(request.score(), request.content());
        return ReviewDto.from(reviewRepository.save(review));
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
}
