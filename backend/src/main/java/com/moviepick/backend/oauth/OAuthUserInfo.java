package com.moviepick.backend.oauth;

// provider가 내려주는 사용자 프로필에서 계정 생성/조회에 필요한 값만 추려낸 것입니다.
// providerUserId는 계정을 특정하는 키이고, nickname/email은 최초 가입 시 닉네임을 정하는 데만 씁니다.
public record OAuthUserInfo(String providerUserId, String nickname, String email) {
}
