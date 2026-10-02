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
}
