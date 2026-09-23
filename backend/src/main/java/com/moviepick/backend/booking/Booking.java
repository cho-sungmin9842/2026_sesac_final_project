package com.moviepick.backend.booking;

import com.moviepick.backend.auth.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "bookings")
@Getter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_id", nullable = false, length = 64)
    private String movieId;

    @Column(name = "movie_title", nullable = false, length = 255)
    private String movieTitle;

    @Column(nullable = false, length = 50)
    private String theater;

    @Column(name = "show_date", nullable = false)
    private LocalDate showDate;

    @Column(nullable = false, length = 10)
    private String showtime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_seats", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_code", length = 10)
    private Set<String> seats = new LinkedHashSet<>();

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Booking() {
    }

    public Booking(
            User user,
            String movieId,
            String movieTitle,
            String theater,
            LocalDate showDate,
            String showtime,
            Set<String> seats,
            int totalPrice
    ) {
        this.user = user;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.theater = theater;
        this.showDate = showDate;
        this.showtime = showtime;
        this.seats = new LinkedHashSet<>(seats);
        this.totalPrice = totalPrice;
        this.createdAt = LocalDateTime.now();
    }
}
