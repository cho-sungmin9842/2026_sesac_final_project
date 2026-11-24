package com.moviepick.backend.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // created_at은 초 단위(DATETIME)라 리뷰 두 개가 같은 초에 쓰이면 동점이 나고, 그 경우 MySQL이
    // 삽입 순서를 보장해주지 않아 "최신순"이 실제 작성 순서와 다르게 뒤섞이는 문제가 있었습니다
    // (2026-10-14 실측 - chat_messages에서 겪었던 것과 같은 패턴). id가 auto_increment라 삽입 순서를
    // 항상 정확히 반영하므로 2차 정렬 기준으로 씁니다(findByUserIdOrderByCreatedAtDescIdDesc와 동일한
    // 이유로, 영화 상세 리뷰 목록과 관리자 "리뷰 관리" 목록에도 2026-11-20에 같은 문제가 발견돼 적용).
    List<Review> findByMovieIdOrderByCreatedAtDescIdDesc(String movieId);

    // 관리자 "리뷰 관리" 화면의 검색/정렬/페이지네이션 - 리뷰가 많아져도 매번 전체를 불러와 브라우저에서
    // 거르지 않도록 2026-11-21에 서버 쪽으로 옮겼습니다. movieTitle 비교는 reviews.movie_title이 아니라
    // movies.title(있으면 그 값, 없으면 저장된 값)로 맞춥니다 - 화면에 보여주는 값(ReviewDto)도 그 기준이라
    // 둘이 다르면 드롭다운에서 고른 제목으로 필터링해도 하나도 안 걸리는 문제가 생깁니다. 정렬 기준(sort)은
    // "latest"/"rating"/"reportCount" 중 하나만 들어오고, 나머지 두 CASE는 모든 행에서 NULL이라 순서에
    // 영향을 주지 않습니다 - 그 뒤로 createdAt/id가 항상 최종 동점 처리 기준이 됩니다(2026-11-20에 고친
    // "같은 초에 쓰인 리뷰 순서 뒤섞임" 버그와 같은 이유로 둘 다 넣어둠).
    // content: 리뷰 내용 텍스트 검색(부분 일치, 대소문자 구분 없음) - 영화 제목/작성자처럼 드롭다운으로
    // 고르는 값이 아니라 자유 입력이라, 따로 lower()로 감싸 대소문자를 가립니다(2026-11-24 요청).
    @Query(
            value = """
                    select r from Review r
                    where (:movieTitle is null or coalesce((select m.title from Movie m where m.movieId = r.movieId), r.movieTitle) = :movieTitle)
                      and (:authorNickname is null or r.user.nickname = :authorNickname)
                      and (:reportedOnly = false or (select count(rr) from ReviewReport rr where rr.review = r) > 0)
                      and (:dateFrom is null or r.createdAt >= :dateFrom)
                      and (:dateTo is null or r.createdAt < :dateTo)
                      and (:content is null or lower(r.content) like lower(concat('%', :content, '%')))
                    order by
                      case when :sort = 'rating' then r.score end desc,
                      case when :sort = 'reportCount' then (select count(rr2) from ReviewReport rr2 where rr2.review = r) end desc,
                      r.createdAt desc,
                      r.id desc
                    """,
            countQuery = """
                    select count(r) from Review r
                    where (:movieTitle is null or coalesce((select m.title from Movie m where m.movieId = r.movieId), r.movieTitle) = :movieTitle)
                      and (:authorNickname is null or r.user.nickname = :authorNickname)
                      and (:reportedOnly = false or (select count(rr) from ReviewReport rr where rr.review = r) > 0)
                      and (:dateFrom is null or r.createdAt >= :dateFrom)
                      and (:dateTo is null or r.createdAt < :dateTo)
                      and (:content is null or lower(r.content) like lower(concat('%', :content, '%')))
                    """
    )
    Page<Review> searchForAdmin(
            @Param("movieTitle") String movieTitle,
            @Param("authorNickname") String authorNickname,
            @Param("reportedOnly") boolean reportedOnly,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("content") String content,
            @Param("sort") String sort,
            Pageable pageable
    );

    // "전체 선택"(페이지를 넘나드는 선택)을 누르면 지금 필터에 맞는 리뷰 전체의 id가 필요합니다 - 체크박스
    // 상태만 결정하면 되니 리뷰 내용 전체가 아니라 id만 가볍게 받아옵니다.
    @Query("""
            select r.id from Review r
            where (:movieTitle is null or coalesce((select m.title from Movie m where m.movieId = r.movieId), r.movieTitle) = :movieTitle)
              and (:authorNickname is null or r.user.nickname = :authorNickname)
              and (:reportedOnly = false or (select count(rr) from ReviewReport rr where rr.review = r) > 0)
              and (:dateFrom is null or r.createdAt >= :dateFrom)
              and (:dateTo is null or r.createdAt < :dateTo)
              and (:content is null or lower(r.content) like lower(concat('%', :content, '%')))
            """)
    List<Long> findIdsForAdmin(
            @Param("movieTitle") String movieTitle,
            @Param("authorNickname") String authorNickname,
            @Param("reportedOnly") boolean reportedOnly,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("content") String content
    );

    // 필터 드롭다운(영화 제목/작성자 닉네임) 선택지 - 실제로 리뷰가 존재하는 조합만, 표시 제목도 위
    // searchForAdmin과 같은 기준(movies.title 우선)으로 내려줍니다. 두 드롭다운을 서로 연동시키는 데도
    // 이 조합 목록을 그대로 씁니다(AdminReportsPage 참고).
    @Query("""
            select distinct
                coalesce((select m.title from Movie m where m.movieId = r.movieId), r.movieTitle) as movieTitle,
                r.user.nickname as authorNickname
            from Review r
            """)
    List<FilterOptionRow> findDistinctFilterOptions();

    interface FilterOptionRow {
        String getMovieTitle();

        String getAuthorNickname();
    }

    // 관리자 "리뷰 관리" 상단 통계 카드의 "오늘 작성된 리뷰" - 검색/필터와 무관하게 DB 전체 기준입니다
    // (searchForAdmin이 필터링된 페이지만 돌려주게 된 뒤로 따로 둬야 함). "전체 리뷰"는 JpaRepository가
    // 이미 제공하는 count()를 그대로 씁니다.
    @Query("select count(r) from Review r where r.createdAt >= :todayStart")
    long countCreatedOnOrAfter(@Param("todayStart") LocalDateTime todayStart);

    // 마이페이지 "내가 쓴 리뷰" 탭 - 영화 구분 없이 이 사용자가 쓴 모든 리뷰.
    List<Review> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    // 한 사용자가 같은 영화에 리뷰를 두 번 쓰지 못하게 막는 용도(프론트의 "리뷰 쓰기" 버튼 비활성화와
    // 같은 규칙을 서버에서도 강제 - AI 채팅처럼 그 버튼을 거치지 않는 경로로도 중복 작성되지 않도록).
    boolean existsByUserIdAndMovieId(Long userId, String movieId);

    // 관리자 "회원 관리" 화면 - 회원별 작성 리뷰 수.
    long countByUserId(Long userId);

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
