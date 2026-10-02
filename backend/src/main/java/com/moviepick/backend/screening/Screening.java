package com.moviepick.backend.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 상영정보 1건(어떤 영화가, 어느 상영관에서, 언제 상영되는지). movieId는 movies 테이블의 합성 id
 * ("{movieId}_{movieSeq}")를 그대로 저장합니다(다른 도메인의 movie_id 컬럼들과 동일한 관례).
 * 매주 월요일 자정 배치(ScreeningGenerationService)가 그 주(월~일) 분량을 한 번에 생성합니다.
 */
@Entity
@Table(name = "screenings")
@Getter
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movie_id", nullable = false, length = 64)
    private String movieId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected Screening() {
    }

    public Screening(String movieId, Theater theater, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.movieId = movieId;
        this.theater = theater;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
