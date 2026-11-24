package com.moviepick.backend.review.dto;

// 관리자 "리뷰 관리" 상단 통계 카드 - 검색/필터와 무관하게 DB에 저장된 리뷰 전체 기준입니다
// (listAllForAdmin이 필터링된 한 페이지만 돌려주므로, 전체 집계는 따로 받아와야 합니다).
public record AdminReviewStatsDto(long totalCount, long todayCount) {
}
