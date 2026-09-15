package com.codeit.otboo.worker.notification;

import com.codeit.otboo.worker.notification.service.NotificationSaveService;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;

import static org.assertj.core.api.Assertions.*;

import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.SingleNotificationCreateEvent;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.codeit.otboo.support.notification.kafka.NotificationTopics;
import com.codeit.otboo.worker.WorkerApplication;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(classes = WorkerApplication.class, properties = "notification.kafka.enabled=true")
@EmbeddedKafka(partitions = 1, topics = {NotificationTopics.CREATE, NotificationTopics.BROADCAST},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "TEST_NOTIFICATION_DB_URL", matches = ".+")
class NotificationPipelineIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TEST_NOTIFICATION_DB_URL"));
        properties.add("spring.datasource.username", () -> "notification_test");
        properties.add("spring.datasource.password", () -> "notification_test");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationSaveService save;
    @Autowired NotificationEventPublisher publisher;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired KafkaListenerEndpointRegistry listeners;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void schema() {
        // 실행 대상은 전용 임시 DB여야 한다. 기존 프로젝트 DB에서 실행하지 않는다.
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .as("전용 notification_test DB에서만 실행")
                .isEqualTo("notification_test");
        jdbc.execute("DROP TABLE IF EXISTS notification");
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY, deleted_at TIMESTAMPTZ)");
        jdbc.execute("""
                CREATE TABLE notification (
                    id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL,
                    receiver_id UUID NOT NULL REFERENCES users(id), title VARCHAR(100) NOT NULL,
                    content VARCHAR(1000) NOT NULL, level VARCHAR(20) NOT NULL
                    CHECK (level IN ('INFO','WARNING','ERROR')), read_at TIMESTAMPTZ)
                """);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V3__add_notification_type_and_deduplication_key.sql"))
                .execute(jdbc.getDataSource());
        listeners.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    private SingleNotificationCreateEvent payload() {
        UUID receiver = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id) VALUES (?)", receiver);
        return new SingleNotificationCreateEvent(receiver, "권한 변경", "관리자로 변경되었습니다", NotificationLevel.INFO);
    }

    @Test
    void kafkaRequestIsCommittedBeforeBroadcastAndDuplicateIsNotBroadcast() throws Exception {
        var payload = payload();
        var message = new NotificationCreateMessage<>(UUID.randomUUID(), 1, NotificationType.ROLE_CHANGED,
                OffsetDateTime.parse("2026-09-14T12:00:00+09:00"), "ROLE_CHANGED:" + UUID.randomUUID(), payload);
        var props = KafkaTestUtils.consumerProps("verify-" + UUID.randomUUID(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        var decoder = new JsonDeserializer<>(NotificationBroadcastEvent.class, NotificationKafkaJson.mapper(), false);
        try (var consumer = new KafkaConsumer<String, NotificationBroadcastEvent>(props, new StringDeserializer(), decoder)) {
            broker.consumeFromAnEmbeddedTopic(consumer, true, NotificationTopics.BROADCAST);
            publisher.publishCreate(payload.receiverId().toString(), message).get(10, TimeUnit.SECONDS);
            var record = KafkaTestUtils.getSingleRecord(consumer, NotificationTopics.BROADCAST, Duration.ofSeconds(20));
            assertThat(record.value().notification().receiverId()).isEqualTo(payload.receiverId());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE id = ?", Integer.class,
                    record.value().notification().id())).isEqualTo(1);
            publisher.publishCreate(payload.receiverId().toString(), message).get(10, TimeUnit.SECONDS);
            assertThat(consumer.poll(Duration.ofSeconds(2))).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void concurrentDuplicatesAndReadNotificationsKeepOneRow() throws Exception {
        var payload = payload();
        String key = "ROLE_CHANGED:" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(4);
        try {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> save.save(NotificationType.ROLE_CHANGED, key, payload).isPresent()));
            }
            int inserted = 0;
            for (var result : results) {
                if (result.get(10, TimeUnit.SECONDS)) inserted++;
            }
            assertThat(inserted).isEqualTo(1);
            jdbc.update("UPDATE notification SET read_at = now()");
            assertThat(save.save(NotificationType.ROLE_CHANGED, key, payload)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollbackLeavesNoNotificationAndDeletedReceiverIsRejected() {
        var payload = payload();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            save.save(NotificationType.ROLE_CHANGED, "ROLE_CHANGED:rollback", payload);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
        jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", payload.receiverId());
        assertThatThrownBy(() -> save.save(NotificationType.ROLE_CHANGED, "ROLE_CHANGED:deleted", payload))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
