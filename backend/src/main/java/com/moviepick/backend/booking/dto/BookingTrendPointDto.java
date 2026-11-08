package com.moviepick.backend.booking.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 "예매/상영관 현황" 화면의 기간별 예매 추이 그래프 한 점. date에 예매가 하나도 없던 날도
 * 0건/0원으로 빠짐없이 채워서 내려줍니다(그래프가 중간에 끊기지 않도록). byMovie는 그래프 막대에
 * 마우스를 올렸을 때 "무슨 영화가 몇 건/얼마"인지 보여주는 용도로, 예매 건수가 많은 영화부터
 * 정렬되어 있습니다.
 */
public record BookingTrendPointDto(LocalDate date, long count, long revenue, List<MovieBookingCount> byMovie) {

    public record MovieBookingCount(String movieTitle, long count, long revenue) {
    }
}
