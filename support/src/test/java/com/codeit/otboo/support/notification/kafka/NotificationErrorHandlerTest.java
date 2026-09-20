package com.codeit.otboo.support.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;

class NotificationErrorHandlerTest {
    private DefaultErrorHandler handler() {
        var factory = NotificationConsumers.factory(new KafkaProperties(),
                NotificationKafkaJson.mapper().constructType(String.class), "test-worker", "earliest");
        return (DefaultErrorHandler) factory.createContainer("notification-create").getCommonErrorHandler();
    }

    @Test
    void permanentNotificationErrorsAreRecoveredWithoutRetryEvenWhenWrapped() {
        for (ErrorCode code : new ErrorCode[]{ErrorCode.INVALID_INPUT_VALUE,
                ErrorCode.USER_NOT_FOUND, ErrorCode.UNSUPPORTED_NOTIFICATION_TYPE, ErrorCode.NOTIFICATION_SOURCE_NOT_FOUND}) {
            var exception = new ListenerExecutionFailedException("listener failed", new NotificationException(code));
            assertThat(handler().handleOne(exception,
                    new ConsumerRecord<>("notification-create", 0, 0, "key", "value"),
                    mock(Consumer.class), mock(MessageListenerContainer.class))).isTrue();
        }
    }

    @Test
    void transientDatabaseErrorAndInterruptionStillRequestRetry() {
        for (Exception exception : new Exception[]{
                new DataAccessResourceFailureException("database down"),
                new NotificationException(ErrorCode.NOTIFICATION_PROCESSING_INTERRUPTED)}) {
            assertThat(handler().handleOne(exception,
                    new ConsumerRecord<>("notification-create", 0, 0, "key", "value"),
                    mock(Consumer.class), mock(MessageListenerContainer.class))).isFalse();
        }
    }
}
