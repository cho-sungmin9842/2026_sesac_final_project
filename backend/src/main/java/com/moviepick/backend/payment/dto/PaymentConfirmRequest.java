package com.moviepick.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

// 토스페이먼츠 결제창이 돌려준 승인 정보(paymentKey/orderId/amount)와, 그 결제로 확정할 예매 내용
// (screeningId/seatIds/ticketCounts)을 함께 받습니다. 결제 승인이 성공해야만 예매가 생성됩니다.
public record PaymentConfirmRequest(
        @NotBlank String paymentKey,
        @NotBlank String orderId,
        @Positive long amount,
        @NotNull Long screeningId,
        @NotEmpty(message = "좌석을 1개 이상 선택해주세요.") List<@NotNull Long> seatIds,
        @NotEmpty(message = "인원 구분을 선택해주세요.") Map<String, Integer> ticketCounts
) {
}
