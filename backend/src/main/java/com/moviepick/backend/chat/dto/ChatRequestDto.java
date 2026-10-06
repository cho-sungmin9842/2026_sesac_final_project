package com.moviepick.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;

// conversationId가 없으면(새 대화의 첫 메시지) 서버가 대화방을 새로 만들고, 있으면 그 대화방에 이어붙입니다.
public record ChatRequestDto(@NotBlank String message, Long conversationId) {
}
