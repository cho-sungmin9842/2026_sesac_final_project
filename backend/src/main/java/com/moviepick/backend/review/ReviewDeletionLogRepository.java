package com.moviepick.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewDeletionLogRepository extends JpaRepository<ReviewDeletionLog, Long> {

    // 관리자 "리뷰 관리" 화면의 "삭제 이력" 탭 - 최근 삭제된 것부터 보여줍니다.
    List<ReviewDeletionLog> findAllByOrderByDeletedAtDesc();
}
