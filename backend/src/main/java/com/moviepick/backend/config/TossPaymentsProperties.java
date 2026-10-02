package com.moviepick.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossPaymentsProperties(String baseUrl, String secretKey) {
}
