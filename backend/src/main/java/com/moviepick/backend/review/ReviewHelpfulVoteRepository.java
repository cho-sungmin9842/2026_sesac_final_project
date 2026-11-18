package com.moviepick.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewHelpfulVoteRepository extends JpaRepository<ReviewHelpfulVote, Long> {

    Optional<ReviewHelpfulVote> findByReviewIdAndUserId(Long reviewId, Long userId);

    long countByReviewId(Long reviewId);

    // ReviewService.toDtos - 리뷰 목록 화면에서 "내가 이미 추천한 리뷰"를 한 번에 알아내기 위한 배치 조회
    // (리뷰마다 따로 조회하면 N+1이 됩니다).
    List<ReviewHelpfulVote> findByUserIdAndReviewIdIn(Long userId, Collection<Long> reviewIds);

    // ReviewService.toDtos - 리뷰 목록의 추천 수를 리뷰마다 따로 세지 않고 한 번에 집계합니다.
    @Query("select v.review.id as reviewId, count(v) as cnt from ReviewHelpfulVote v where v.review.id in :reviewIds group by v.review.id")
    List<HelpfulCountRow> countByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);

    interface HelpfulCountRow {
        Long getReviewId();

        Long getCnt();
    }
}
