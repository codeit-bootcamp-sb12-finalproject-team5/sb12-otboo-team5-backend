package com.codeit.otboo.api.common.init;

import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        UUID id,
        String email,
        String password,
        String name
) {
}
