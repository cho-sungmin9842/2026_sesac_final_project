package com.moviepick.backend.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

// 상영 1건당 좌석 하나(8행 A~H x 14열, A열은 전부 휠체어석). 상영정보 생성 배치가 상영정보와 함께 만듭니다.
@Entity
@Table(name = "seats")
@Getter
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screening_id", nullable = false)
    private Long screeningId;

    @Column(name = "row_label", nullable = false, length = 1)
    private String rowLabel;

    @Column(name = "col_no", nullable = false)
    private int colNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status;

    protected Seat() {
    }

    public Seat(Long screeningId, String rowLabel, int colNo, SeatType seatType, SeatStatus status) {
        this.screeningId = screeningId;
        this.rowLabel = rowLabel;
        this.colNo = colNo;
        this.seatType = seatType;
        this.status = status;
    }

    // 예매가 실제로 확정됐을 때 좌석 상태를 BOOKED로 바꿉니다(서버가 호출 전에 이미 AVAILABLE인지 확인합니다).
    public void markBooked() {
        this.status = SeatStatus.BOOKED;
    }
}
