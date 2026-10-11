package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 이메일/SMS 같은 발송 수단이 없는 데모 인증이라, 가입 시 입력한 아이디+닉네임이 일치하면 본인으로 보고
// 서버가 임시 비밀번호를 발급해줍니다(사용자가 새 비밀번호를 직접 입력하지 않습니다).
public record ResetPasswordRequest(
        @NotBlank(message = "아이디를 입력해주세요.") String username,
        @NotBlank(message = "닉네임을 입력해주세요.") String nickname
) {
}
