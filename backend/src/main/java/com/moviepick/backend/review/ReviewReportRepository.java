package com.moviepick.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    // 중복 신고를 막기 전에 미리 확인합니다(DB 유니크 제약이 최종 방어선이지만, 그 전에 친절한
    // 에러 메시지로 먼저 막습니다).
    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);
}
