package com.moviepick.backend.oauth.dto;

import jakarta.validation.constraints.NotBlank;

// 로그인 화면(OAuthCompletePage)이 콜백 리다이렉트로 받은 1회성 토큰을 실제 사용자 정보로 바꿀 때 보냅니다.
public record OAuthExchangeRequest(@NotBlank String token) {
}
