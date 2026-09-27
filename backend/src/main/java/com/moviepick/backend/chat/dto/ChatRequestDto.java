package com.moviepick.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequestDto(@NotBlank String message) {
}
