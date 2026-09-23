package com.moviepick.backend.booking.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record BookingRequest(
        @NotBlank String movieId,
        @NotBlank String movieTitle,
        @NotBlank String theater,
        @NotNull @FutureOrPresent(message = "지난 날짜는 예매할 수 없습니다.") LocalDate showDate,
        @NotBlank String showtime,
        @NotEmpty(message = "좌석을 1개 이상 선택해주세요.") List<@NotBlank String> seats
) {
}
