package com.codeit.otboo.api.common.init;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        String email,
        String password,
        String name
) {
}
