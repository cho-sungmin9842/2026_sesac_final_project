package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.ReviewDto;
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
    public List<ReviewDto> list(@PathVariable String movieId) {
        return reviewService.list(movieId);
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
}
