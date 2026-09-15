package com.codeit.otboo.support.notification.kafka;

import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.fasterxml.jackson.databind.JavaType;
import java.util.HashMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NotificationConsumers {
    private NotificationConsumers() {}

    public static <T> ConcurrentKafkaListenerContainerFactory<String, T> factory(
            KafkaProperties properties, JavaType type, String group, String reset) {
        var config = new HashMap<String, Object>(properties.buildConsumerProperties());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, reset);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        var deserializer = new JsonDeserializer<T>(type, NotificationKafkaJson.mapper(), false);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(), new ErrorHandlingDeserializer<>(deserializer)));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        var errors = new DefaultErrorHandler((record, exception) ->
                log.error("NOTIFICATION_FAILED topic={} partition={} offset={} group={}",
                        record.topic(), record.partition(), record.offset(), group, exception),
                new FixedBackOff(1000L, 2L));
        errors.addNotRetryableExceptions(IllegalArgumentException.class);
        errors.setBackOffFunction((record, exception) -> {
            // Kafka가 감싼 예외에서도 원래 알림 오류 코드를 확인한다.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof NotificationException notificationException) {
                    return switch (notificationException.getErrorCode()) {
                        case INVALID_INPUT_VALUE, USER_NOT_FOUND, UNSUPPORTED_NOTIFICATION_TYPE,
                                INVALID_NOTIFICATION_BROADCAST ->
                                new FixedBackOff(0L, 0L);
                        default -> null; // 기존 1초 간격 2회 재시도를 유지한다.
                    };
                }
            }
            return null;
        });

        factory.setCommonErrorHandler(errors);
        return factory;
    }
}
