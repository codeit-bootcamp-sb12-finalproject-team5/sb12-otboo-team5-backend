package com.codeit.otboo.api.notification.service;

import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DeleteConsumerGroupsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.ConsumerGroupState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationConsumerGroupCleanup {
    private static final String PREFIX = "notification-sse-";
    private static final long MIN_AGE_MILLIS = Duration.ofHours(1).toMillis();
    private final KafkaAdmin kafkaAdmin;
    private final String currentGroup;

    public NotificationConsumerGroupCleanup(KafkaAdmin kafkaAdmin,
            @Qualifier("notificationBroadcastFactory")
            ConcurrentKafkaListenerContainerFactory<String, NotificationBroadcastEvent> factory) {
        this.kafkaAdmin = kafkaAdmin;
        this.currentGroup = (String) factory.getConsumerFactory().getConfigurationProperties()
                .get(ConsumerConfig.GROUP_ID_CONFIG);
    }

    // Kafka 관리 요청이 SSE heartbeat 스케줄러를 막지 않도록 기존 비동기 실행기를 사용한다.
    @Async("taskExecutor")
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void cleanup() {
        Admin admin = null;
        try {
            admin = Admin.create(kafkaAdmin.getConfigurationProperties());
            deleteUnusedGroups(admin);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            // 정리 실패는 알림 소비에 영향을 주지 않으며 다음 실행에서 다시 확인한다.
            log.warn("SSE consumer group 정리 실패", exception);
        } finally {
            if (admin != null) {
                admin.close(Duration.ofSeconds(1));
            }
        }
    }

    void deleteUnusedGroups(Admin admin) throws Exception {
        long cutoff = System.currentTimeMillis() - MIN_AGE_MILLIS;
        var groups = admin.listConsumerGroups(new ListConsumerGroupsOptions().timeoutMs(5000))
                .all().get(6, TimeUnit.SECONDS).stream()
                .filter(group -> group.state().orElse(ConsumerGroupState.UNKNOWN) == ConsumerGroupState.EMPTY)
                .map(group -> group.groupId())
                .filter(group -> isOldSseGroup(group, cutoff))
                .toList();
        if (groups.isEmpty()) {
            return;
        }
        // 조회 이후 다시 활성화된 그룹은 Kafka가 삭제를 거부한다.
        admin.deleteConsumerGroups(groups, new DeleteConsumerGroupsOptions().timeoutMs(5000))
                .all().get(6, TimeUnit.SECONDS);
        log.info("사용하지 않는 SSE consumer group {}개 정리", groups.size());
    }

    private boolean isOldSseGroup(String group, long cutoff) {
        if (group.equals(currentGroup) || !group.startsWith(PREFIX)) {
            return false;
        }
        try {
            String suffix = group.substring(PREFIX.length());
            UUID id = UUID.fromString(suffix);
            // API가 생성한 UUIDv7 그룹만 정리한다. 상위 48비트는 생성 시각(ms)이다.
            return id.toString().equals(suffix) && id.version() == 7 && id.variant() == 2
                    && (id.getMostSignificantBits() >>> 16) < cutoff;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
