package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 목업의 카카오/네이버/구글 버튼은 실제 OAuth 연동이 아니라, provider별로 고정된 데모 계정을 만들거나 재사용합니다.
public record SocialLoginRequest(@NotBlank String provider) {
}
