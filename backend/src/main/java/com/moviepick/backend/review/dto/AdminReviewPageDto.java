package com.moviepick.backend.review.dto;

import java.util.List;

// 관리자 "리뷰 관리" 목록 - 서버 쪽에서 거르고 정렬하고 페이지로 잘라서 내려줍니다(리뷰가 많아지면
// 클라이언트가 전체를 들고 거르는 방식이 느려지므로 2026-11-21에 옮김). page는 0부터 시작합니다
// (Pageable 관례 - 프론트의 1-based 페이지 번호는 AdminReportsPage에서 주고받을 때 변환합니다).
public record AdminReviewPageDto(
        List<ReviewDto> content,
        int page,
        int totalPages,
        long totalElements
) {
}
