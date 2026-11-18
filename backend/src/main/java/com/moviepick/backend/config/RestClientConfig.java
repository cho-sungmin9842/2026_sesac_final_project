package com.moviepick.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient kmdbRestClient(KmdbProperties kmdbProperties) {
        return RestClient.builder()
                .baseUrl(kmdbProperties.baseUrl())
                .build();
    }

    @Bean
    public RestClient geminiRestClient(GeminiProperties geminiProperties) {
        return RestClient.builder()
                .baseUrl(geminiProperties.baseUrl())
                .build();
    }

    @Bean
    public RestClient tossRestClient(TossPaymentsProperties tossPaymentsProperties) {
        return RestClient.builder()
                .baseUrl(tossPaymentsProperties.baseUrl())
                .build();
    }

    // 구글 OAuth 클라이언트용 - 인가 서버와 사용자 정보 서버의 도메인이 달라 baseUrl을 고정하지
    // 않고 호출마다 전체 URL을 씁니다.
    @Bean
    public RestClient oauthRestClient() {
        return RestClient.builder().build();
    }
}
