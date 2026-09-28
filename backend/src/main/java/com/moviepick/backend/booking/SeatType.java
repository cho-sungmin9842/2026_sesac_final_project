package com.moviepick.backend.booking;

// 일반석 / 장애인(휠체어)석 구분. 어떤 seat_code가 ACCESSIBLE인지는 BookingService가 판정합니다.
public enum SeatType {
    REGULAR,
    ACCESSIBLE
}
