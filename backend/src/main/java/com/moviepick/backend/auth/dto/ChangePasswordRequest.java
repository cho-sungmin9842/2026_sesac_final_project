package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.") String currentPassword,
        @NotBlank(message = "새 비밀번호를 입력해주세요.") @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.") String newPassword
) {
}
