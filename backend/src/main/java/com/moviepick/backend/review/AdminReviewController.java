package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.ReviewDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 관리자 "리뷰 신고 관리" 화면 전용 - 특정 영화로 좁히는 일반 ReviewController와 달리, 전체 리뷰를
// 조회/삭제합니다. 요청자가 실제로 관리자인지는 ReviewService에서 X-User-Id로 확인합니다.
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewDto> listAll(@RequestHeader("X-User-Id") Long userId) {
        return reviewService.listAllForAdmin(userId);
    }

    @DeleteMapping("/{reviewId}")
    public void delete(@PathVariable Long reviewId, @RequestHeader("X-User-Id") Long userId) {
        reviewService.adminDelete(reviewId, userId);
    }
}
