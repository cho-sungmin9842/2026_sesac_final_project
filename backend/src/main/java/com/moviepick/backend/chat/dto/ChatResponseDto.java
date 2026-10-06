package com.moviepick.backend.chat.dto;

import com.moviepick.backend.movie.dto.MovieSummaryDto;

import java.util.List;

public record ChatResponseDto(
        Long conversationId, String reply, List<MovieSummaryDto> movies, SeatStatusDto seatStatus
) {
}
