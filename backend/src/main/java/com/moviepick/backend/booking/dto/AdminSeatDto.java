package com.moviepick.backend.booking.dto;

import com.moviepick.backend.screening.Seat;

/**
 * 관리자 "예매/상영관 현황" 좌석 배치도 전용. 일반 사용자용 SeatDto와 달리 예매자 닉네임/아이디와
 * bookingId를 함께 내려줘, 예매 완료 좌석을 클릭했을 때 누가 예매했는지 보여주고 그 자리에서 바로
 * 예매를 취소할 수 있습니다(관리자만 볼 수 있는 정보라 일반 사용자가 쓰는 SeatDto/엔드포인트에는
 * 절대 섞지 않습니다).
 */
public record AdminSeatDto(
        Long id,
        String rowLabel,
        int colNo,
        String seatType,
        String status,
        String bookerNickname,
        String bookerUsername,
        Long bookingId
) {
    public static AdminSeatDto from(Seat seat, String bookerNickname, String bookerUsername, Long bookingId) {
        return new AdminSeatDto(
                seat.getId(), seat.getRowLabel(), seat.getColNo(), seat.getSeatType().name(),
                seat.effectiveStatus().name(), bookerNickname, bookerUsername, bookingId);
    }
}
