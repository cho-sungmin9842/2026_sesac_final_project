package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.ReviewReportDetailDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 알림 벨의 리뷰 신고 알림 "보러가기" - 신고 상세(리뷰 내용/작성자/신고 사유/신고자)를 보여주고,
// 그 자리에서 바로 처리(리뷰 삭제 / 리뷰 삭제 취소)할 수 있게 합니다.
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

    // 리뷰는 그대로 두고 이 신고 건의 삭제 처리만 취소합니다 - 리뷰 자체를 지우려면 AdminReviewController의
    // DELETE /api/admin/reviews/{reviewId}를 씁니다.
    @DeleteMapping("/{reportId}")
    public void cancelDeletion(@PathVariable Long reportId, @RequestHeader("X-User-Id") Long adminUserId) {
        reviewService.cancelReviewDeletion(adminUserId, reportId);
    }
}
