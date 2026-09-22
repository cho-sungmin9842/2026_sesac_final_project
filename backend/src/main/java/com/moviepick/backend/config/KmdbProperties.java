package com.moviepick.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kmdb")
public record KmdbProperties(String baseUrl, String serviceKey) {
}
