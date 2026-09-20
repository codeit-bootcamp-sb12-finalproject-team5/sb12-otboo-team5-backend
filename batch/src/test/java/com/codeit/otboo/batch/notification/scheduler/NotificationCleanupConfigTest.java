package com.codeit.otboo.batch.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.otboo.batch.notification.config.NotificationCleanupConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NotificationCleanupConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NotificationCleanupConfig.class, NotificationConsumerGroupCleanup.class)
            .withBean(KafkaAdmin.class, () -> new KafkaAdmin(Map.of()));

    @Test
    void cleanupIsDisabledByDefault() {
        context.run(application -> {
            assertThat(application).doesNotHaveBean(NotificationConsumerGroupCleanup.class);
            assertThat(application).doesNotHaveBean("notificationCleanupExecutor");
        });
    }

    @Test
    void enabledCleanupUsesBatchExecutorWithoutApiBeans() {
        context.withPropertyValues("notification.kafka.enabled=true").run(application -> {
            assertThat(application).hasSingleBean(NotificationConsumerGroupCleanup.class);
            assertThat(AopUtils.isAopProxy(application.getBean(NotificationConsumerGroupCleanup.class))).isTrue();
            var executor = application.getBean("notificationCleanupExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("notification-cleanup-");
        });
    }
}
