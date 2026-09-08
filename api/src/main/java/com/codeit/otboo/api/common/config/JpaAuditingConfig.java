package com.codeit.otboo.api.common.config;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

/**
 * 엔티티의 시간 필드가 OffsetDateTime 이므로,
 * Auditing 이 기본 생성하는 LocalDateTime 대신 OffsetDateTime 을 제공합니다.
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return new DateTimeProvider() {
            @Override
            public Optional<TemporalAccessor> getNow() {
                return Optional.of(OffsetDateTime.now());
            }
        };
    }
}
