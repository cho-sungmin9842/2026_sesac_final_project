package com.moviepick.backend.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByMovieIdAndTheaterAndShowDateAndShowtime(
            String movieId, String theater, LocalDate showDate, String showtime
    );

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
}
