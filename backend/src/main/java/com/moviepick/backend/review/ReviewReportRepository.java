package com.moviepick.backend.review;

import com.moviepick.backend.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    // 중복 신고를 막기 전에 미리 확인합니다(DB 유니크 제약이 최종 방어선이지만, 그 전에 친절한
    // 에러 메시지로 먼저 막습니다).
    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);

    // ReviewService.adminDelete - 리뷰를 지우기 전에 이 리뷰를 신고했던 사람이 누구누구인지 알아내,
    // 삭제 처리 결과를 알려줄 대상을 정합니다. ReviewReport 엔티티 자체를(그 안의 EAGER review 연관
    // 관계까지) 영속성 컨텍스트에 올리면, 바로 뒤에 그 리뷰를 삭제할 때 Hibernate가 "삭제 중인
    // 엔티티를 아직 참조하는 영속 인스턴스가 있다"며 TransientObjectException을 던집니다(2026-11-18
    // 실측) - reporter만 바로 조회해서 이 문제를 피합니다.
    @Query("select distinct rr.reporter from ReviewReport rr where rr.review.id = :reviewId")
    List<User> findReportersByReviewId(@Param("reviewId") Long reviewId);

    // ReviewService.adminDelete - 리뷰 작성자에게 삭제 사유를 알려주기 위해, 이 리뷰에 달린 신고
    // 사유(ReviewReportReason.name()) 코드를 전부 모읍니다. 신고 없이 관리자가 직접 지운 경우는
    // 빈 목록이 돌아옵니다.
    @Query("select distinct rr.reason from ReviewReport rr where rr.review.id = :reviewId")
    List<String> findReasonCodesByReviewId(@Param("reviewId") Long reviewId);

    // ReviewService.listAllForAdmin - "리뷰 관리" 테이블에 리뷰마다 "신고 N건" 뱃지를 달기 위해, 리뷰마다
    // 따로 조회하지 않고 한 번에 집계합니다(ReviewHelpfulVoteRepository.countByReviewIds와 같은 패턴).
    @Query("select rr.review.id as reviewId, count(rr) as cnt from ReviewReport rr where rr.review.id in :reviewIds group by rr.review.id")
    List<ReportCountRow> countByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);

    // "리뷰 관리" 테이블의 "신고 N건" 뱃지를 눌렀을 때 - 이 리뷰에 달린 신고 전부(신고자/사유/시각)를
    // 최신순으로 보여줍니다. 삭제가 뒤따르지 않는 단순 조회라 TransientObjectException 문제와는
    // 무관합니다(findReportersByReviewId의 주석 참고).
    List<ReviewReport> findByReviewIdOrderByCreatedAtDesc(Long reviewId);

    interface ReportCountRow {
        Long getReviewId();

        Long getCnt();
    }
}
