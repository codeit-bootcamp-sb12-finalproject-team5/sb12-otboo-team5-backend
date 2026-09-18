package com.codeit.otboo.worker.notification.config;

import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.support.notification.kafka.NotificationConsumers;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;

@EnableKafka
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationCreateConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationCreateMessage<JsonNode>>
            notificationCreateFactory(KafkaProperties properties,
                                      ObjectProvider<MeterRegistry> registries) {
        return NotificationConsumers.factory(
            properties,
            NotificationKafkaJson.mapper()
                .getTypeFactory()
                .constructParametricType(
                    NotificationCreateMessage.class
                    , JsonNode.class
                ),
            "notification-worker",
            "earliest",
            consumers -> registries.ifAvailable(
                registry -> consumers.addListener(new MicrometerConsumerListener<>(registry)))
        );
    }
}
