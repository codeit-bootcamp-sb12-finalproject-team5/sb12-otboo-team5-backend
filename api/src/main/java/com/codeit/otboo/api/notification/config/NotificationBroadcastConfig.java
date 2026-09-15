package com.codeit.otboo.api.notification.config;

import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.support.notification.kafka.NotificationConsumers;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.util.Assert;

@EnableKafka
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationBroadcastConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationBroadcastEvent>
            notificationBroadcastFactory(
                KafkaProperties properties,
                @Value("${notification.kafka.instance-id}") String instanceId
    ) {
        Assert.hasText(instanceId, "notification.kafka.instance-id is required");

        return NotificationConsumers.factory(
            properties,
            NotificationKafkaJson
                .mapper()
                .constructType(NotificationBroadcastEvent.class),
            "notification-sse-" + instanceId, "latest"
        );
    }
}
