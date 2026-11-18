package com.moviepick.backend.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.config.OAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

// 구글 로그인(https://console.cloud.google.com/apis/credentials) - OAuth 동의 화면을 먼저 설정하고
// "OAuth 클라이언트 ID"(웹 애플리케이션)를 발급받아야 합니다.
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthClient {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    private final RestClient oauthRestClient;
    private final OAuthProperties.Provider properties;

    public GoogleOAuthClient(RestClient oauthRestClient, OAuthProperties oAuthProperties) {
        this.oauthRestClient = oauthRestClient;
        this.properties = oAuthProperties.google();
    }

    @Override
    public String providerName() {
        return "google";
    }

    @Override
    public boolean isConfigured() {
        return properties != null && hasText(properties.clientId());
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URL)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public OAuthUserInfo exchangeCodeForUserInfo(String code) {
        String accessToken = fetchAccessToken(code);
        return fetchUserInfo(accessToken);
    }

    private String fetchAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);

        try {
            TokenResponse response = oauthRestClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new ApiException("구글 토큰 교환 응답이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY);
            }
            return response.accessToken();
        } catch (RestClientException e) {
            log.error("구글 토큰 교환에 실패했습니다.", e);
            throw new ApiException("구글 로그인에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        try {
            UserResponse response = oauthRestClient.get()
                    .uri(USER_INFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(UserResponse.class);
            if (response == null || response.id() == null) {
                throw new ApiException("구글 사용자 정보 응답이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY);
            }
            return new OAuthUserInfo(response.id(), response.name(), response.email());
        } catch (RestClientException e) {
            log.error("구글 사용자 정보 조회에 실패했습니다.", e);
            throw new ApiException("구글 로그인에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserResponse(String id, String email, String name) {
    }
}
