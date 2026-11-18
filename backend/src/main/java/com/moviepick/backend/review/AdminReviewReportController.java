package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.ReviewReportDetailDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 알림 벨의 리뷰 신고 알림 "보러가기" - 신고 상세(리뷰 내용/작성자/신고 사유/신고자)를 보여주는
// 다이얼로그용입니다.
@RestController
@RequestMapping("/api/admin/review-reports")
public class AdminReviewReportController {

    private final ReviewService reviewService;

    public AdminReviewReportController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/{reportId}")
    public ReviewReportDetailDto get(@PathVariable Long reportId, @RequestHeader("X-User-Id") Long adminUserId) {
        return reviewService.getReportDetailForAdmin(adminUserId, reportId);
    }
}
