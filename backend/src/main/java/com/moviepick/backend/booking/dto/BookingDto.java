package com.moviepick.backend.booking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record BookingDto(
        Long id,
        Long screeningId,
        String movieId,
        String movieTitle,
        String theaterName,
        LocalDate showDate,
        String showtime,
        List<String> seats,
        int totalPrice,
        LocalDateTime createdAt,
        // 인원 구분("adult"/"teen"/"child"/"senior")별 인원 수 - 0명인 구분은 아예 안 들어있습니다.
        Map<String, Integer> ticketCounts
) {
}
