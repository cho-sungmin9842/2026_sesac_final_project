package com.moviepick.backend.oauth;

// provider별로 인가 URL 파라미터, 토큰 교환 방식, 사용자 정보 응답 형식이 달라 구현체를 따로
// 둡니다(현재는 GoogleOAuthClient만 있지만, 다른 provider를 추가하면 이 인터페이스를 구현하면 됩니다).
public interface OAuthClient {

    // 이 provider 이름 - OAuthController가 URL 경로("google")로 구현체를 찾을 때 씁니다.
    String providerName();

    // 사용자를 이 URL로 리다이렉트하면 provider 로그인/동의 화면이 뜹니다. state는 CSRF 방지용 1회성 값입니다.
    String buildAuthorizeUrl(String state);

    // 콜백으로 받은 code를 provider 토큰 서버에 교환하고, 그 토큰으로 사용자 정보를 받아옵니다.
    OAuthUserInfo exchangeCodeForUserInfo(String code);

    // client-id/secret이 아직 비어있으면(콘솔에 앱을 등록하지 않은 상태) 로그인 시도를 막습니다.
    boolean isConfigured();
}
