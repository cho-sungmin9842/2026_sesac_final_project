package com.moviepick.backend.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

@Embeddable
@Getter
public class SeatSelection {

    @Column(name = "seat_code", length = 10)
    private String seatCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", length = 20, nullable = false)
    private SeatType seatType;

    protected SeatSelection() {
    }

    public SeatSelection(String seatCode, SeatType seatType) {
        this.seatCode = seatCode;
        this.seatType = seatType;
    }
}
