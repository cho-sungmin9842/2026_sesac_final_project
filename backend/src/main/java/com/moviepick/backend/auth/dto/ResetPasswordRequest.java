package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 이메일/SMS 같은 발송 수단이 없는 데모 인증이라, 가입 시 입력한 아이디+닉네임이 일치하면 본인으로 보고
// 바로 새 비밀번호로 바꿔줍니다.
public record ResetPasswordRequest(
        @NotBlank(message = "아이디를 입력해주세요.") String username,
        @NotBlank(message = "닉네임을 입력해주세요.") String nickname,
        @NotBlank(message = "새 비밀번호를 입력해주세요.") @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.") String newPassword
) {
}
