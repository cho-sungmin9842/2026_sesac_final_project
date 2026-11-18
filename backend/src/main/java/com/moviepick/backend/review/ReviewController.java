package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.ReviewDto;
import com.moviepick.backend.review.dto.ReviewHelpfulResponse;
import com.moviepick.backend.review.dto.ReviewReportRequest;
import com.moviepick.backend.review.dto.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies/{movieId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewDto> list(
            @PathVariable String movieId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return reviewService.list(movieId, userId);
    }

    @PostMapping
    public ReviewDto create(
            @PathVariable String movieId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return reviewService.create(movieId, userId, request);
    }

    @PutMapping("/{reviewId}")
    public ReviewDto update(
            @PathVariable String movieId,
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return reviewService.update(movieId, reviewId, userId, request);
    }

    @DeleteMapping("/{reviewId}")
    public void delete(
            @PathVariable String movieId,
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        reviewService.delete(movieId, reviewId, userId);
    }

    /**
     * 본인이 작성하지 않은 리뷰를 신고합니다 - ReviewList.jsx의 "신고" 버튼용.
     */
    @PostMapping("/{reviewId}/report")
    public void report(
            @PathVariable String movieId,
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ReviewReportRequest request
    ) {
        reviewService.reportReview(movieId, reviewId, userId, request.reason());
    }

    /**
     * 본인이 작성하지 않은 리뷰를 "도움이 됐어요"로 추천하거나, 다시 눌러서 추천을 취소합니다
     * (신고와 대칭되는 긍정적 상호작용) - ReviewList.jsx의 "도움이 됐어요" 버튼용.
     */
    @PostMapping("/{reviewId}/helpful")
    public ReviewHelpfulResponse toggleHelpful(
            @PathVariable String movieId,
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return reviewService.toggleHelpful(movieId, reviewId, userId);
    }
}
