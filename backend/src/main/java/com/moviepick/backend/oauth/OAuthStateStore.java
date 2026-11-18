package com.moviepick.backend.oauth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// OAuthController가 /start에서 발급하고 /callback에서 소모하는 1회성 CSRF 방지 토큰입니다.
// 로그인 과정 중에만 잠깐 쓰여 DB에 둘 필요 없이 메모리로 충분합니다(서버 재시작 시 끊겨도 다시
// 로그인을 시작하면 그만입니다).
@Component
public class OAuthStateStore {

    private static final long TTL_SECONDS = 300;

    private final Map<String, Instant> validUntilByState = new ConcurrentHashMap<>();

    public String issue() {
        String state = UUID.randomUUID().toString();
        validUntilByState.put(state, Instant.now().plusSeconds(TTL_SECONDS));
        return state;
    }

    public boolean consume(String state) {
        Instant validUntil = validUntilByState.remove(state);
        return validUntil != null && Instant.now().isBefore(validUntil);
    }
}
