package com.codeit.otboo.batch.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationCleanupConfig {

    // 날씨 스케줄러가 Kafka 관리 요청을 기다리지 않도록 알림 전용 실행기를 사용한다.
    @Bean
    public ThreadPoolTaskExecutor notificationCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("notification-cleanup-");
        return executor;
    }
}
