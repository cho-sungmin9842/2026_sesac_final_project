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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 영화 예매 1건. 상영관/날짜/시간/영화 정보는 문자열로 중복 저장하지 않고 screeningId 하나로만 연결합니다
 * (screenings를 movies/theaters와 조인하면 전부 구할 수 있습니다 - 다른 도메인의 movie_id 컬럼들과
 * 동일하게, Screening을 JPA 관계가 아니라 평범한 id 컬럼으로 참조합니다).
 */
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

    @Column(name = "screening_id", nullable = false)
    private Long screeningId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_seats", joinColumns = @JoinColumn(name = "booking_id"))
    private List<BookingSeat> seats = new ArrayList<>();

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Booking() {
    }

    public Booking(User user, Long screeningId, List<BookingSeat> seats, int totalPrice) {
        this.user = user;
        this.screeningId = screeningId;
        this.seats = new ArrayList<>(seats);
        this.totalPrice = totalPrice;
        this.createdAt = LocalDateTime.now();
    }
}
