package com.moviepick.backend.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

// 예매 1건에 포함된 좌석 하나. seatId는 screening.Seat.id를 그대로 참조하고(좌석 종류/위치는 거기서 조회),
// ticketCategory(성인/청소년/어린이/우대)만 여기 따로 저장합니다.
@Embeddable
@Getter
public class BookingSeat {

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "ticket_category", length = 20, nullable = false)
    private String ticketCategory;

    protected BookingSeat() {
    }

    public BookingSeat(Long seatId, String ticketCategory) {
        this.seatId = seatId;
        this.ticketCategory = ticketCategory;
    }
}
