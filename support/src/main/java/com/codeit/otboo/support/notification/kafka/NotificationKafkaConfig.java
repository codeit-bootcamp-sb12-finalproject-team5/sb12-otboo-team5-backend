package com.codeit.otboo.support.notification.kafka;

import java.util.HashMap;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaProperties.class)
public class NotificationKafkaConfig {

    @Bean
    public DefaultKafkaProducerFactory<String, Object> notificationProducerFactory(KafkaProperties properties) {
        var config = new HashMap<String, Object>(properties.buildProducerProperties());

        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);

        var serializer = new JsonSerializer<Object>(NotificationKafkaJson.mapper());
        serializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), serializer);
    }

    @Bean
    public KafkaTemplate<String, Object> notificationKafkaTemplate(
            DefaultKafkaProducerFactory<String, Object> notificationProducerFactory) {
        return new KafkaTemplate<>(notificationProducerFactory);
    }

    @Bean
    public NotificationEventPublisher notificationEventPublisher(
            KafkaTemplate<String, Object> notificationKafkaTemplate) {
        return new NotificationEventPublisher(notificationKafkaTemplate);
    }
}
