package com.codeit.otboo.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.otboo.api.notification.config.NotificationBroadcastConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

class NotificationBroadcastConfigTest {
    private final NotificationBroadcastConfig config = new NotificationBroadcastConfig();

    @Test
    void independentFactoriesUseDifferentUuidV7Groups() {
        var first = config.notificationBroadcastFactory(new KafkaProperties(), registries());
        var second = config.notificationBroadcastFactory(new KafkaProperties(), registries());
        String group = (String) first.getConsumerFactory().getConfigurationProperties()
                .get(ConsumerConfig.GROUP_ID_CONFIG);
        String other = (String) second.getConsumerFactory().getConfigurationProperties()
                .get(ConsumerConfig.GROUP_ID_CONFIG);
        assertThat(group).startsWith("notification-sse-").isNotEqualTo(other);
        assertThat(UUID.fromString(group.substring("notification-sse-".length())).version()).isEqualTo(7);
        assertThat(first.getConsumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    }

    // 지표 레지스트리가 없는 환경에서도 컨슈머는 그대로 만들어져야 한다.
    // 모니터링 설정이 빠졌다고 알림이 끊기면 안 된다.
    @Test
    void buildsConsumerEvenWithoutMeterRegistry() {
        var factory = config.notificationBroadcastFactory(new KafkaProperties(), noRegistry());

        assertThat(factory.getConsumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    }

    private ObjectProvider<MeterRegistry> registries() {
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("meterRegistry", new SimpleMeterRegistry());
        return beanFactory.getBeanProvider(MeterRegistry.class);
    }

    private ObjectProvider<MeterRegistry> noRegistry() {
        return new DefaultListableBeanFactory().getBeanProvider(MeterRegistry.class);
    }
}
