package com.moviepick.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * spring-boot-starter-security(필터 체인/기본 로그인 폼 등)는 쓰지 않고,
 * 비밀번호 해시에 필요한 spring-security-crypto의 BCryptPasswordEncoder만 빈으로 등록합니다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
