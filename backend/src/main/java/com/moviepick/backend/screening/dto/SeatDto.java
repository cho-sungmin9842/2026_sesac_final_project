package com.moviepick.backend.screening.dto;

import com.moviepick.backend.screening.Seat;

public record SeatDto(
        Long id,
        String rowLabel,
        int colNo,
        String seatType,
        String status
) {
    public static SeatDto from(Seat seat) {
        return new SeatDto(seat.getId(), seat.getRowLabel(), seat.getColNo(), seat.getSeatType().name(), seat.getStatus().name());
    }
}
