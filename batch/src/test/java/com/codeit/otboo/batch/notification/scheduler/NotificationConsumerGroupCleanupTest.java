package com.codeit.otboo.batch.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.uuid.Generators;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

class NotificationConsumerGroupCleanupTest {
    private static final String OLD = "notification-sse-01900000-0000-7000-8000-000000000001";
    private final KafkaProperties properties = new KafkaProperties();
    private final NotificationConsumerGroupCleanup cleanup = new NotificationConsumerGroupCleanup(
            new org.springframework.kafka.core.KafkaAdmin(properties.buildAdminProperties(null)));
    private final Admin admin = mock(Admin.class);

    @Test
    void deletesOnlyOldEmptySseGroups() throws Exception {
        listing(List.of(group(OLD, ConsumerGroupState.EMPTY),
                group("notification-sse-" + Generators.timeBasedEpochGenerator().generate(), ConsumerGroupState.EMPTY),
                group("notification-worker", ConsumerGroupState.EMPTY),
                group("notification-sse-manual", ConsumerGroupState.EMPTY),
                group("notification-sse-" + UUID.randomUUID(), ConsumerGroupState.EMPTY),
                group(OLD.replace("000001", "000002"), ConsumerGroupState.STABLE),
                group(OLD.replace("000001", "000003"), ConsumerGroupState.PREPARING_REBALANCE),
                new ConsumerGroupListing(OLD.replace("000001", "000004"), false, Optional.empty())));
        var result = mock(DeleteConsumerGroupsResult.class);
        when(admin.deleteConsumerGroups(eq(List.of(OLD)), any(DeleteConsumerGroupsOptions.class))).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));

        cleanup.deleteUnusedGroups(admin);

        verify(admin).deleteConsumerGroups(eq(List.of(OLD)), any(DeleteConsumerGroupsOptions.class));
    }

    @Test
    void doesNotSendDeleteWhenThereAreNoCandidates() throws Exception {
        listing(List.of(group("notification-worker", ConsumerGroupState.EMPTY)));
        cleanup.deleteUnusedGroups(admin);
        verify(admin, never()).deleteConsumerGroups(any(), any());
    }

    @Test
    void doesNotForceDeleteWhenGroupBecomesActive() {
        listing(List.of(group(OLD, ConsumerGroupState.EMPTY)));
        var result = mock(DeleteConsumerGroupsResult.class);
        when(admin.deleteConsumerGroups(any(), any())).thenReturn(result);
        var failure = new org.apache.kafka.common.internals.KafkaFutureImpl<Void>();
        failure.completeExceptionally(new GroupNotEmptyException("active"));
        when(result.all()).thenReturn(failure);
        assertThatThrownBy(() -> cleanup.deleteUnusedGroups(admin))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(GroupNotEmptyException.class);
        verify(admin, times(1)).deleteConsumerGroups(any(), any());
    }

    @Test
    void scheduledFailureDoesNotEscapeAndClosesAdminClient() {
        when(admin.listConsumerGroups(any(ListConsumerGroupsOptions.class)))
                .thenThrow(new IllegalStateException("unavailable"));
        try (var creation = mockStatic(Admin.class)) {
            creation.when(() -> Admin.create(any(java.util.Map.class))).thenReturn(admin);
            cleanup.cleanup();
        }
        verify(admin).close(java.time.Duration.ofSeconds(1));
    }

    private ConsumerGroupListing group(String id, ConsumerGroupState state) {
        return new ConsumerGroupListing(id, false, Optional.of(state));
    }

    private void listing(Collection<ConsumerGroupListing> groups) {
        var result = mock(ListConsumerGroupsResult.class);
        when(admin.listConsumerGroups(any(ListConsumerGroupsOptions.class))).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(groups));
    }
}
