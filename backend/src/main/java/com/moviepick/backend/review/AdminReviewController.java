package com.moviepick.backend.review;

import com.moviepick.backend.review.dto.AdminReviewPageDto;
import com.moviepick.backend.review.dto.AdminReviewStatsDto;
import com.moviepick.backend.review.dto.ReviewDeletionLogDto;
import com.moviepick.backend.review.dto.ReviewFilterOptionDto;
import com.moviepick.backend.review.dto.ReviewReportDetailDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
    public AdminReviewPageDto listAll(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String movieTitle,
            @RequestParam(required = false) String authorNickname,
            @RequestParam(defaultValue = "false") boolean reportedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String content
    ) {
        return reviewService.listAllForAdmin(userId, page, size, sort, movieTitle, authorNickname, reportedOnly, dateFrom, dateTo, content);
    }

    // "전체 선택"(페이지를 넘나드는 선택) - 지금 필터에 맞는 리뷰 전체의 id만 가볍게 돌려줍니다.
    @GetMapping("/ids")
    public List<Long> listIds(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String movieTitle,
            @RequestParam(required = false) String authorNickname,
            @RequestParam(defaultValue = "false") boolean reportedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String content
    ) {
        return reviewService.getAdminReviewIds(userId, movieTitle, authorNickname, reportedOnly, dateFrom, dateTo, content);
    }

    // 필터 드롭다운(영화 제목/작성자 닉네임) 선택지 - 실제로 리뷰가 존재하는 조합만.
    @GetMapping("/filter-options")
    public List<ReviewFilterOptionDto> filterOptions(@RequestHeader("X-User-Id") Long userId) {
        return reviewService.getAdminFilterOptions(userId);
    }

    // 상단 통계 카드("전체 리뷰"/"오늘 작성된 리뷰") - 검색/필터와 무관하게 DB 전체 기준입니다.
    @GetMapping("/stats")
    public AdminReviewStatsDto stats(@RequestHeader("X-User-Id") Long userId) {
        return reviewService.getAdminStats(userId);
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
