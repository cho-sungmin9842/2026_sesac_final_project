package com.moviepick.backend.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record BookingRequest(
        @NotNull Long screeningId,
        @NotEmpty(message = "좌석을 1개 이상 선택해주세요.") List<@NotNull Long> seatIds,
        // 연령 구분(adult/teen/child/senior)별 인원 수. 좌석 수와 합이 같아야 하고, 요금은 여기 값이 아니라
        // 서버가 상영 날짜/시간 기준(시간대·주중/주말·공휴일)으로 직접 계산합니다(클라이언트가 가격을 조작할 수 없도록).
        @NotEmpty(message = "인원 구분을 선택해주세요.") Map<String, Integer> ticketCounts
) {
}
