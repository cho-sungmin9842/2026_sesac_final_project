package com.moviepick.backend.review.dto;

// 리뷰 신고 요청 본문. reason은 ReviewReportReason의 name() 중 하나여야 합니다.
public record ReviewReportRequest(String reason) {
}
