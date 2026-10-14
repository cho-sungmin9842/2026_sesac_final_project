package com.moviepick.backend.screening.dto;

import com.moviepick.backend.screening.Seat;
import com.moviepick.backend.screening.SeatStatus;

// bookedByMe는 호출자가 X-User-Id를 보냈고 그 좌석을 실제로 그 사용자가 예매했을 때만 true입니다
// (마이페이지 좌석 배치도 다이얼로그에서 "내 좌석/다른 사람 좌석"을 구분하는 용도 - 예매 화면의 좌석
// 선택처럼 그 구분이 필요 없는 곳에서는 항상 false로 내려갑니다).
// status는 Seat.effectiveStatus()로 지연 만료가 이미 반영된 값입니다("AVAILABLE"/"HELD"/"BOOKED") -
// 호출하는 쪽(프론트 포함)은 held_until 같은 만료 시각을 따로 볼 필요 없이 이 값과 heldByMe만으로
// 좌석 상태를 판단하면 됩니다. heldByMe는 그 좌석을 지금 호출자 본인이 선점 중일 때만 true입니다.
public record SeatDto(
        Long id,
        String rowLabel,
        int colNo,
        String seatType,
        String status,
        boolean bookedByMe,
        boolean heldByMe
) {
    public static SeatDto from(Seat seat, Long viewerUserId, boolean bookedByMe) {
        SeatStatus effective = seat.effectiveStatus();
        boolean heldByMe = effective == SeatStatus.HELD && seat.isHeldBy(viewerUserId);
        return new SeatDto(
                seat.getId(), seat.getRowLabel(), seat.getColNo(), seat.getSeatType().name(),
                effective.name(), bookedByMe, heldByMe);
    }
}
