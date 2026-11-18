package com.moviepick.backend.oauth;

import com.moviepick.backend.auth.AuthService;
import com.moviepick.backend.auth.dto.UserDto;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.config.OAuthProperties;
import com.moviepick.backend.oauth.dto.OAuthExchangeRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 구글 실제 OAuth 로그인. 이 앱은 쿠키/세션이 아니라 로그인 응답을 localStorage에
// 저장하는 데모 인증이라(AuthContext.jsx), provider 콜백이 끝나면 사용자 id를 바로 넘기지 않고
// OAuthSessionExchangeStore의 1회성 토큰으로 프런트엔드(OAuthCompletePage)에 돌려보냅니다.
@Slf4j
@RestController
@RequestMapping("/api/auth/oauth")
public class OAuthController {

    private final Map<String, OAuthClient> clientsByProvider;
    private final OAuthStateStore stateStore;
    private final OAuthSessionExchangeStore exchangeStore;
    private final OAuthProperties oAuthProperties;
    private final AuthService authService;

    public OAuthController(
            List<OAuthClient> clients,
            OAuthStateStore stateStore,
            OAuthSessionExchangeStore exchangeStore,
            OAuthProperties oAuthProperties,
            AuthService authService
    ) {
        this.clientsByProvider = clients.stream().collect(Collectors.toMap(OAuthClient::providerName, Function.identity()));
        this.stateStore = stateStore;
        this.exchangeStore = exchangeStore;
        this.oAuthProperties = oAuthProperties;
        this.authService = authService;
    }

    @GetMapping("/{provider}/start")
    public void start(@PathVariable String provider, HttpServletResponse response) throws IOException {
        OAuthClient client = resolveClient(provider);
        if (!client.isConfigured()) {
            response.sendRedirect(buildFrontendRedirect(
                    "아직 " + provider + " 로그인이 설정되지 않았습니다. 관리자에게 문의하세요.", null));
            return;
        }
        String state = stateStore.issue();
        response.sendRedirect(client.buildAuthorizeUrl(state));
    }

    @GetMapping("/{provider}/callback")
    public void callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response
    ) throws IOException {
        OAuthClient client = resolveClient(provider);

        if (error != null) {
            response.sendRedirect(buildFrontendRedirect("로그인이 취소되었습니다.", null));
            return;
        }
        if (code == null || state == null || !stateStore.consume(state)) {
            response.sendRedirect(buildFrontendRedirect("로그인 요청이 유효하지 않습니다. 다시 시도해주세요.", null));
            return;
        }

        try {
            OAuthUserInfo userInfo = client.exchangeCodeForUserInfo(code);
            UserDto user = authService.loginOrCreateOAuthUser(provider, userInfo);
            String token = exchangeStore.issue(user.id());
            response.sendRedirect(buildFrontendRedirect(null, token));
        } catch (ApiException e) {
            log.warn("{} OAuth 로그인 실패: {}", provider, e.getMessage());
            response.sendRedirect(buildFrontendRedirect(e.getMessage(), null));
        }
    }

    @PostMapping("/exchange")
    public UserDto exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        Long userId = exchangeStore.consume(request.token())
                .orElseThrow(() -> new ApiException("유효하지 않거나 만료된 로그인 요청입니다.", HttpStatus.BAD_REQUEST));
        return authService.getUserForOAuthExchange(userId);
    }

    private OAuthClient resolveClient(String provider) {
        OAuthClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new ApiException("지원하지 않는 로그인 방식입니다: " + provider, HttpStatus.BAD_REQUEST);
        }
        return client;
    }

    private String buildFrontendRedirect(String errorMessage, String token) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(oAuthProperties.frontendRedirectUri());
        if (token != null) {
            builder.queryParam("token", token);
        } else {
            builder.queryParam("error", errorMessage);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }
}
