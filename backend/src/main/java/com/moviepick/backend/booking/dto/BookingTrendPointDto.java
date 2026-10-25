package com.moviepick.backend.booking.dto;

import java.time.LocalDate;

/**
 * 관리자 "예매/상영관 현황" 화면의 기간별 예매 추이 그래프 한 점. date에 예매가 하나도 없던 날도
 * 0건으로 빠짐없이 채워서 내려줍니다(그래프가 중간에 끊기지 않도록).
 */
public record BookingTrendPointDto(LocalDate date, long count) {
}
