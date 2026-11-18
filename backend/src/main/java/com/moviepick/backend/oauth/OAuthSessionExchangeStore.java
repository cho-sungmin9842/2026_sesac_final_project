package com.moviepick.backend.oauth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 이 앱은 쿠키/세션이 아니라 로그인 응답으로 받은 user를 localStorage에 저장하는 방식이라(X-User-Id
// 헤더 기반 데모 인증), provider 콜백이 끝난 뒤 브라우저를 프런트엔드로 돌려보낼 때 로그인된 사용자의
// id를 URL에 그대로 실어 보내면 누구나 다른 사람 id를 넣어 그 계정으로 로그인할 수 있게 됩니다.
// 그래서 id 대신 1회용 랜덤 토큰을 발급하고(짧은 TTL), 프런트엔드가 그 토큰으로 /exchange를 호출해야만
// 실제 사용자 정보를 내려줍니다.
@Component
public class OAuthSessionExchangeStore {

    private static final long TTL_SECONDS = 60;

    private final Map<String, Entry> userIdByToken = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        userIdByToken.put(token, new Entry(userId, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    public Optional<Long> consume(String token) {
        Entry entry = userIdByToken.remove(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    private record Entry(Long userId, Instant expiresAt) {
    }
}
