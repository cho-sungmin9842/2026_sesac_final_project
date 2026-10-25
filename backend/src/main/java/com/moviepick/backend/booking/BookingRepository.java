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

    // 관리자 "예매/상영관 현황" 화면의 기간별 예매 추이 그래프 - 날짜별로 그날 실제 결제 완료된(취소된
    // 예매는 cancel()에서 행 자체를 삭제하므로 남아있는 행이 곧 유효한 예매입니다) 예매 건수를 셉니다.
    // 예매가 하나도 없던 날은 결과에 아예 안 나오므로(GROUP BY라 0건인 날을 만들어내지 못함), 서비스
    // 계층에서 빈 날짜를 0건으로 채워 넣습니다.
    @Query(value = "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM bookings WHERE created_at >= :since GROUP BY DATE(created_at)", nativeQuery = true)
    List<Object[]> countDailySince(@Param("since") LocalDateTime since);
}
