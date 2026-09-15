package com.codeit.otboo.api.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.codeit.otboo.api.notification.event.NotificationCommittedListener;
import com.codeit.otboo.api.notification.event.NotificationEvents;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationCommittedListenerTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {}

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object tx, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    @Test
    void publishesOnlyAfterCommitAndNeverOnRollbackOrOutsideTransaction() {
        var publisher = mock(NotificationEventPublisher.class);
        when(publisher.publishCreate(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        try (var context = context(publisher)) {
            var transaction = new TransactionTemplate(new TestTransactionManager());
            var message = NotificationEvents.roleChanged(UUID.randomUUID(), UserRole.ADMIN);
            context.publishEvent(message);
            verifyNoInteractions(publisher);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(message);
                verifyNoInteractions(publisher);
                status.setRollbackOnly();
            });
            verifyNoInteractions(publisher);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(message);
                verifyNoInteractions(publisher);
            });
            verify(publisher).publishCreate(message.payload().receiverId().toString(), message);
        }
    }

    @Test
    void kafkaFailureDoesNotEscapeCommittedBusinessTransaction() {
        var publisher = mock(NotificationEventPublisher.class);
        when(publisher.publishCreate(anyString(), any())).thenThrow(new IllegalStateException("broker down"));
        try (var context = context(publisher)) {
            var transaction = new TransactionTemplate(new TestTransactionManager());
            transaction.executeWithoutResult(status -> context.publishEvent(
                    NotificationEvents.roleChanged(UUID.randomUUID(), UserRole.ADMIN)));
            verify(publisher).publishCreate(anyString(), any());
        }
    }

    private AnnotationConfigApplicationContext context(NotificationEventPublisher publisher) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test",
                        java.util.Map.of("notification.kafka.enabled", "true")));
        context.registerBean(NotificationEventPublisher.class, () -> publisher);
        context.register(Config.class, NotificationCommittedListener.class);
        context.refresh();
        return context;
    }
}
