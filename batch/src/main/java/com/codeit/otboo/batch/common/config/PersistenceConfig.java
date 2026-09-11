package com.codeit.otboo.batch.common.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.codeit.otboo.domain")
@EnableJpaRepositories(basePackages = "com.codeit.otboo.domain")
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class PersistenceConfig {
}
