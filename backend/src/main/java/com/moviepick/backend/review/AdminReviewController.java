package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.ReviewDeletionLogDto;
import com.moviepick.backend.review.dto.ReviewDto;
import com.moviepick.backend.review.dto.ReviewReportDetailDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // "리뷰 관리" 테이블의 "신고 N건" 뱃지 - 이 리뷰에 달린 신고 전부(사유/신고자/시각)를 보여줍니다.
    @GetMapping("/{reviewId}/reports")
    public List<ReviewReportDetailDto> getReports(@PathVariable Long reviewId, @RequestHeader("X-User-Id") Long userId) {
        return reviewService.getReportsForReview(userId, reviewId);
    }

    // "리뷰 관리" 테이블의 일괄 선택 삭제 - 실패한 건(이미 삭제됨 등)이 있으면 그 목록을 돌려줍니다.
    @PostMapping("/bulk-delete")
    public List<String> bulkDelete(@RequestBody List<Long> reviewIds, @RequestHeader("X-User-Id") Long userId) {
        return reviewService.adminDeleteMany(reviewIds, userId);
    }

    // "리뷰 관리" 화면의 "삭제 이력" - 최근 삭제된 것부터.
    @GetMapping("/deletion-logs")
    public List<ReviewDeletionLogDto> getDeletionLogs(@RequestHeader("X-User-Id") Long userId) {
        return reviewService.getDeletionLogs(userId);
    }
}
