package com.moviepick.backend.booking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime createdAt
) {
}
