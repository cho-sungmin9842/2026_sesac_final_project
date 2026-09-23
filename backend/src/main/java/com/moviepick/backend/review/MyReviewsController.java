package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.ReviewDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 마이페이지 "내가 쓴 리뷰" 탭 전용 - ReviewController는 /api/movies/{movieId}/reviews로 특정 영화에 묶여 있어 별도로 뺐습니다.
@RestController
@RequestMapping("/api/reviews/mine")
public class MyReviewsController {

    private final ReviewService reviewService;

    public MyReviewsController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewDto> mine(@RequestHeader("X-User-Id") Long userId) {
        return reviewService.listByUser(userId);
    }
}
