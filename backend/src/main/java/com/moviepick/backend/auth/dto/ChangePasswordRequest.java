package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.") String currentPassword,
        // 회원가입 때와 동일한 규칙: 8자 이상, 영문+숫자 조합 또는 영문+숫자+특수문자 조합만 허용.
        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{}:;,.<>/?~]*$",
                message = "비밀번호는 영문과 숫자를 포함해 영문+숫자 또는 영문+숫자+특수문자 조합만 가능합니다."
        )
        String newPassword
) {
}
