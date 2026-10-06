package com.moviepick.backend.chat.dto;

import com.moviepick.backend.screening.dto.SeatDto;

import java.util.List;

/**
 * AI 추천 채팅에서 "OO일 OO관 OO시 회차 좌석 현황 알려줘"처럼 특정 상영 회차의 좌석 현황을 물어봤을 때
 * 돌려주는 답변입니다. seats는 그 회차의 전체 좌석 배치도(BookingSeatMapDialog와 같은 SeatDto 형태)입니다.
 */
public record SeatStatusDto(
        Long screeningId,
        String movieTitle,
        String theaterName,
        String dateLabel,
        String timeLabel,
        int totalSeats,
        int availableSeats,
        List<SeatDto> seats
) {
}
