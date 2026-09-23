package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "닉네임을 입력해주세요.") String nickname,
        @NotBlank(message = "아이디를 입력해주세요.") String username,
        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.") String password
) {
}
