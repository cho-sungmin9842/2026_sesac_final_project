package com.moviepick.backend.review.dto;

// "리뷰 관리" 필터 드롭다운 - 실제로 리뷰가 존재하는 영화 제목·작성자 닉네임 조합만 보여주기 위해
// 받아옵니다(서버 쪽 페이지네이션으로 옮긴 뒤로는 화면에 한 페이지치 리뷰만 내려가므로, 드롭다운
// 선택지는 따로 전체 조합을 받아와야 합니다). 두 드롭다운을 연동시키는 데도 이 조합 목록을 그대로
// 씁니다 - 작성자를 먼저 고르면, 그 작성자가 실제로 쓴 리뷰들의 조합만 남겨서 영화 제목 쪽을
// 좁히는 식입니다(AdminReportsPage 참고).
public record ReviewFilterOptionDto(String movieTitle, String authorNickname) {
}
