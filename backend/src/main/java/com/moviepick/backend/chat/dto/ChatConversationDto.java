package com.moviepick.backend.chat.dto;

import java.time.LocalDateTime;

/**
 * AI 추천 채팅 사이드바에 보여줄 대화방 한 줄. title은 그 대화의 첫 메시지를 간단히 줄인 값입니다.
 */
public record ChatConversationDto(Long id, String title, LocalDateTime createdAt, LocalDateTime lastMessageAt) {
}
