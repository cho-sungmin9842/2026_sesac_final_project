package com.moviepick.backend.screening.dto;

import com.moviepick.backend.screening.Seat;

// bookedByMe는 호출자가 X-User-Id를 보냈고 그 좌석을 실제로 그 사용자가 예매했을 때만 true입니다
// (마이페이지 좌석 배치도 다이얼로그에서 "내 좌석/다른 사람 좌석"을 구분하는 용도 - 예매 화면의 좌석
// 선택처럼 그 구분이 필요 없는 곳에서는 항상 false로 내려갑니다).
public record SeatDto(
        Long id,
        String rowLabel,
        int colNo,
        String seatType,
        String status,
        boolean bookedByMe
) {
    public static SeatDto from(Seat seat, boolean bookedByMe) {
        return new SeatDto(seat.getId(), seat.getRowLabel(), seat.getColNo(), seat.getSeatType().name(), seat.getStatus().name(), bookedByMe);
    }
}
