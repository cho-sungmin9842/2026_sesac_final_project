package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank(message = "비밀번호를 입력해주세요.") String password) {
}
