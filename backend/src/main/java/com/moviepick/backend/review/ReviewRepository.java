package com.moviepick.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMovieIdOrderByCreatedAtDesc(String movieId);

    // 관리자 "리뷰 신고 관리" 화면 - 특정 영화/사용자로 좁히지 않고 DB에 저장된 모든 사용자의 리뷰를 보여줍니다.
    List<Review> findAllByOrderByCreatedAtDesc();

    // 마이페이지 "내가 쓴 리뷰" 탭 - 영화 구분 없이 이 사용자가 쓴 모든 리뷰.
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 영화 목록 화면 카드에 별점 배지를 달아주기 위해, 여러 영화의 평균 평점을 한 번에 집계합니다.
    @Query("""
            select r.movieId as movieId, avg(r.score) as averageScore, count(r) as reviewCount
            from Review r
            where r.movieId in :movieIds
            group by r.movieId
            """)
    List<MovieRatingRow> aggregateRatings(@Param("movieIds") Collection<String> movieIds);

    // 홈 화면 "지금 인기 있는 영화" - 고정된 제목 목록이 아니라, 실제 평균 평점이 기준 이상인 영화를 평점 높은 순으로 찾습니다.
    @Query("""
            select r.movieId as movieId, avg(r.score) as averageScore, count(r) as reviewCount
            from Review r
            group by r.movieId
            having avg(r.score) >= :minScore
            order by avg(r.score) desc, count(r) desc
            """)
    List<MovieRatingRow> findTopRated(@Param("minScore") double minScore);

    interface MovieRatingRow {
        String getMovieId();

        Double getAverageScore();

        Long getReviewCount();
    }
}
