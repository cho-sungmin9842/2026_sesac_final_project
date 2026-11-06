package com.moviepick.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yml의 app.version 값(화면에 표시하는 앱 버전, 예: V.1.0)입니다.
@ConfigurationProperties(prefix = "app")
public record AppVersionProperties(String version) {
}
