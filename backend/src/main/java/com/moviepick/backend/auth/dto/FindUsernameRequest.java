package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 아이디를 잊어버린 사용자용 - 이메일/SMS가 없는 데모 인증이라, 닉네임+비밀번호가 일치하면
// 본인으로 보고 아이디를 알려줍니다(ResetPasswordRequest의 반대 방향: 거긴 아이디+닉네임으로
// 비밀번호를 새로 발급, 여긴 닉네임+비밀번호로 아이디를 조회).
public record FindUsernameRequest(
        @NotBlank(message = "닉네임을 입력해주세요.") String nickname,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {
}
