package com.codeit.otboo.api.common.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * API 애플리케이션에서 domain 모듈의 JPA Entity와 Repository를 스캔하고,
 * 엔티티 생성, 수정 시각 자동 저장을 위한 JPA Auditing을 활성화하는 설정 클래스이다.
 */
@Configuration
@EntityScan(basePackages = "com.codeit.otboo.domain")
@EnableJpaRepositories(basePackages = "com.codeit.otboo.domain")
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class PersistenceConfig {
}