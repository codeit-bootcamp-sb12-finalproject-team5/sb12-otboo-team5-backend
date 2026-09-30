package com.codeit.otboo.api.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
        String frontendRedirectUri
) {
}
