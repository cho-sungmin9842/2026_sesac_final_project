package com.moviepick.backend.chat.dto;

import com.moviepick.backend.movie.dto.MovieSummaryDto;

import java.util.List;

/**
 * 저장된 채팅 메시지 한 줄. role은 "user" 또는 "ai"입니다.
 * movies는 그 메시지가 추천한 영화 목록(추천 메시지가 아니면 빈 리스트)입니다.
 */
public record ChatMessageDto(String role, String content, List<MovieSummaryDto> movies) {
}
