package com.moviepick.backend.booking.dto;

import com.moviepick.backend.booking.Booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BookingDto(
        Long id,
        String movieId,
        String movieTitle,
        String theater,
        LocalDate showDate,
        String showtime,
        List<String> seats,
        int totalPrice,
        LocalDateTime createdAt
) {
    public static BookingDto from(Booking booking) {
        return new BookingDto(
                booking.getId(),
                booking.getMovieId(),
                booking.getMovieTitle(),
                booking.getTheater(),
                booking.getShowDate(),
                booking.getShowtime(),
                List.copyOf(booking.getSeats()),
                booking.getTotalPrice(),
                booking.getCreatedAt()
        );
    }
}
