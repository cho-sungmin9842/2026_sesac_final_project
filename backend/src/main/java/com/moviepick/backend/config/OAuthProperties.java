package com.moviepick.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 구글 소셜 로그인 설정. client-id/client-secret은 Google Cloud Console에 앱을 등록해야 발급받을 수
// 있어, application-local.yml(커밋되지 않음)에 채워 넣습니다.
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(String frontendRedirectUri, Provider google) {

    public record Provider(String clientId, String clientSecret, String redirectUri) {
    }
}
