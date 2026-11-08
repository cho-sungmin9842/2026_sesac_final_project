package com.moviepick.backend.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    // 마이페이지 "예매 내역" 탭 - 최신 예매부터 보여줍니다.
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 마이페이지 좌석 배치도 다이얼로그 - 이 상영의 좌석별로 누가 예매했는지(user_id) 알아내는 데 씁니다.
    List<Booking> findByScreeningId(Long screeningId);

    // 상영 임박 알림 배치 - 상영일이 [from, to] 범위인 예매만 가져옵니다(Booking은 Screening을 JPA 관계가 아니라
    // screening_id 컬럼으로만 참조하므로 서브쿼리로 상영일을 거릅니다).
    @Query("select b from Booking b where b.screeningId in (select s.id from Screening s where s.date between :from and :to)")
    List<Booking> findByScreeningDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // 관리자 "회원 관리" 화면 - 회원별 예매 건수.
    long countByUserId(Long userId);

    // 관리자 "예매/상영관 현황" 화면의 기간별 예매 추이 그래프 - 날짜·영화별로 그날 실제 결제 완료된
    // (취소된 예매는 cancel()에서 행 자체를 삭제하므로 남아있는 행이 곧 유효한 예매입니다) 예매 건수와
    // 매출액(total_price 합)을 함께 셉니다. 그래프에 마우스를 올렸을 때 "무슨 영화가 몇 건/얼마"인지
    // 보여주려고 영화 제목까지 묶어서 집계하고, 서비스 계층에서 날짜별로 다시 합쳐 총 건수·총 매출과
    // 영화별 내역을 함께 만듭니다. 예매가 하나도 없던 날은 결과에 아예 안 나오므로(GROUP BY라 0건인
    // 날을 만들어내지 못함), 서비스 계층에서 빈 날짜를 0건/0원으로 채워 넣습니다.
    @Query(value = "SELECT DATE(b.created_at) AS d, m.title AS title, COUNT(*) AS c, SUM(b.total_price) AS revenue "
            + "FROM bookings b "
            + "JOIN screenings s ON b.screening_id = s.id "
            + "JOIN movies m ON s.movie_id = m.movie_id "
            + "WHERE b.created_at >= :since "
            + "GROUP BY DATE(b.created_at), m.title", nativeQuery = true)
    List<Object[]> countDailyByMovieSince(@Param("since") LocalDateTime since);
}
