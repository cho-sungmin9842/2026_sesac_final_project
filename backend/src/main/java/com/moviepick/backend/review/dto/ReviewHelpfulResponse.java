package com.moviepick.backend.review.dto;

// 리뷰 "도움이 됐어요" 토글 결과 - 화면이 전체 리뷰 목록을 다시 불러오지 않고 이 값만으로 바로 갱신합니다.
public record ReviewHelpfulResponse(long helpfulCount, boolean helpfulByMe) {
}
