package com.codeit.otboo.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.otboo.api.notification.config.NotificationBroadcastConfig;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

class NotificationBroadcastConfigTest {
    @Test
    void independentFactoriesUseDifferentUuidV7Groups() {
        var config = new NotificationBroadcastConfig();
        var first = config.notificationBroadcastFactory(new KafkaProperties());
        var second = config.notificationBroadcastFactory(new KafkaProperties());
        String group = (String) first.getConsumerFactory().getConfigurationProperties()
                .get(ConsumerConfig.GROUP_ID_CONFIG);
        String other = (String) second.getConsumerFactory().getConfigurationProperties()
                .get(ConsumerConfig.GROUP_ID_CONFIG);
        assertThat(group).startsWith("notification-sse-").isNotEqualTo(other);
        assertThat(UUID.fromString(group.substring("notification-sse-".length())).version()).isEqualTo(7);
        assertThat(first.getConsumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    }
}
