package com.moviepick.backend.screening;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {
    boolean existsByDateBetween(LocalDate start, LocalDate end);

    // 예매 화면의 상영관/날짜/회차 선택지용. theater는 지연 로딩이라(open-in-view: false), 여기서 즉시 함께 가져옵니다.
    @Query("select s from Screening s join fetch s.theater where s.movieId = :movieId order by s.date asc, s.startTime asc")
    List<Screening> findByMovieIdOrderByDateAscStartTimeAsc(@Param("movieId") String movieId);

    // AI 채팅에서 "10월 15일에 상영하는 영화 알려줘"처럼 특정 날짜를 콕 집어 물었을 때, 그날 실제로
    // 상영하는 영화 전체를 찾는 용도입니다.
    @Query("select s from Screening s join fetch s.theater where s.date = :date order by s.startTime asc")
    List<Screening> findByDateOrderByStartTimeAsc(@Param("date") LocalDate date);

    // AI 채팅에서 "다음주에 상영 예정인 영화 알려줘"처럼 특정 주간을 물었을 때, 그 범위에 실제로 상영하는
    // 영화 전체를 찾는 용도입니다.
    @Query("select s from Screening s join fetch s.theater where s.date between :start and :end order by s.date asc, s.startTime asc")
    List<Screening> findByDateBetweenOrderByDateAscStartTimeAsc(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
