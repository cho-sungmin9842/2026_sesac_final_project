package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        // 닉네임은 영문/숫자/공백/특수문자/한글 등 어떤 조합이든 제한 없이 허용합니다.
        @NotBlank(message = "닉네임을 입력해주세요.") String nickname,
        // 아이디는 영문과 숫자만 쓸 수 있고(한글/공백/특수문자 불가), 영문과 숫자가 둘 다 들어가야 합니다.
        @NotBlank(message = "아이디를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9]+$", message = "아이디는 영문과 숫자 조합만 가능합니다.")
        String username,
        // 비밀번호는 8자 이상이어야 하고, 영문+숫자 조합 또는 영문+숫자+특수문자 조합만 허용합니다(영문과
        // 숫자는 반드시 포함, 특수문자는 선택). 공백/한글은 허용하지 않습니다.
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{}:;,.<>/?~]*$",
                message = "비밀번호는 영문과 숫자를 포함해 영문+숫자 또는 영문+숫자+특수문자 조합만 가능합니다."
        )
        String password
) {
}
