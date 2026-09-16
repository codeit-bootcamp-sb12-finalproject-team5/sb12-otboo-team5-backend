package com.codeit.otboo.worker.notification;

import com.codeit.otboo.worker.notification.service.NotificationSaveService;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import com.codeit.otboo.domain.notification.event.WeatherNotificationCreateEvent;
import com.codeit.otboo.domain.notification.event.RoleChangedNotificationEvent;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.worker.notification.repository.NotificationRecipientRepository;

import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.dto.NotificationContent;
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
    @Autowired NotificationRecipientRepository recipients;
    @Autowired com.codeit.otboo.worker.notification.handler.SingleNotificationHandler singleHandler;
    @Autowired com.codeit.otboo.worker.notification.handler.FanOutNotificationHandler fanOutHandler;
    @Autowired com.codeit.otboo.worker.notification.repository.NotificationSourceRepository sources;
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
        jdbc.execute("DROP TABLE IF EXISTS profile");
        jdbc.execute("DROP TABLE IF EXISTS follow");
        jdbc.execute("DROP TABLE IF EXISTS direct_message");
        jdbc.execute("DROP TABLE IF EXISTS dm_room_member");
        jdbc.execute("DROP TABLE IF EXISTS feed_comment");
        jdbc.execute("DROP TABLE IF EXISTS feed_like");
        jdbc.execute("DROP TABLE IF EXISTS feed");
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY, name VARCHAR(50) NOT NULL DEFAULT '작성자', deleted_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE profile (user_id UUID UNIQUE REFERENCES users(id), weather_grid_id UUID)");
        jdbc.execute("CREATE TABLE follow (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), follower_id UUID REFERENCES users(id), followee_id UUID REFERENCES users(id), UNIQUE(follower_id, followee_id))");
        jdbc.execute("CREATE TABLE feed (id UUID PRIMARY KEY, user_id UUID REFERENCES users(id), content TEXT, is_visible BOOLEAN DEFAULT true, deleted_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE feed_comment (id UUID PRIMARY KEY, feed_id UUID REFERENCES feed(id), user_id UUID REFERENCES users(id), content VARCHAR(1000))");
        jdbc.execute("CREATE TABLE feed_like (id UUID PRIMARY KEY, feed_id UUID REFERENCES feed(id), user_id UUID REFERENCES users(id))");
        jdbc.execute("CREATE TABLE direct_message (id UUID PRIMARY KEY, dm_room_id UUID, sender_id UUID REFERENCES users(id), content TEXT, created_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE dm_room_member (dm_room_id UUID, user_id UUID REFERENCES users(id), joined_at TIMESTAMPTZ, left_at TIMESTAMPTZ, UNIQUE(dm_room_id,user_id))");
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

    private NotificationContent payload() {
        UUID receiver = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id) VALUES (?)", receiver);
        return new NotificationContent(receiver, "권한 변경", "관리자로 변경되었습니다", NotificationLevel.INFO);
    }

    @Test
    void kafkaRequestIsCommittedBeforeBroadcastAndDuplicateIsNotBroadcast() throws Exception {
        var payload = payload();
        var message = new NotificationCreateMessage<>(UUID.randomUUID(), 2, NotificationType.ROLE_CHANGED,
                OffsetDateTime.parse("2026-09-14T12:00:00+09:00"), "ROLE_CHANGED:" + UUID.randomUUID(), new RoleChangedNotificationEvent(payload.receiverId(), UserRole.ADMIN));
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

    @Test
    void weatherKafkaFanOutSavesMultiplePagesAndReplayKeepsRows() throws Exception {
        UUID grid = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id) SELECT md5(i::text)::uuid FROM generate_series(1, 10001) i");
        jdbc.update("INSERT INTO profile(user_id, weather_grid_id) SELECT id, ? FROM users", grid);
        jdbc.update("UPDATE users SET deleted_at = now() WHERE id = md5('10001')::uuid");
        var message = new NotificationCreateMessage<>(UUID.randomUUID(), 2, NotificationType.WEATHER_RAIN,
                OffsetDateTime.now(), "WEATHER_RAIN:2026-09-13", new WeatherNotificationCreateEvent(
                        grid, "RAIN", OffsetDateTime.parse("2026-09-12T22:00:00Z")));
        publisher.publishCreate(grid.toString(), message).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isEqualTo(10000));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE title = ? AND content = ?",
                Integer.class, "9월 13일 비 예보", "9월 13일 7시부터 비가 올 것으로 예상됩니다.")).isEqualTo(10000);
        // 원본 페이지 재처리: 중복 저장이 0건이어도 후속 페이지까지 도달해야 한다.
        jdbc.update("DELETE FROM notification WHERE receiver_id = (SELECT id FROM users WHERE deleted_at IS NULL ORDER BY id DESC LIMIT 1)");
        publisher.publishCreate(grid.toString(), message).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isEqualTo(10000));
    }

    @Test
    void followerQueryFiltersDeletedUsersAndResumesInDatabaseUuidOrder() {
        UUID author = new UUID(0, 1), first = new UUID(0, 2), deleted = new UUID(0, 3), last = new UUID(0, 4);
        for (UUID id : java.util.List.of(author, first, deleted, last)) {
            jdbc.update("INSERT INTO users(id) VALUES (?)", id);
        }
        for (UUID follower : java.util.List.of(first, deleted, last)) {
            jdbc.update("INSERT INTO follow(follower_id, followee_id) VALUES (?, ?)", follower, author);
        }
        jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", deleted);
        assertThat(recipients.findFollowers(author, null, 1)).containsExactly(first);
        assertThat(recipients.findFollowers(author, first, 501)).containsExactly(last);
        assertThat(recipients.findFollowers(author, last, 501)).isEmpty();
    }

    @Test
    void pageFailureRollsBackEarlierRecipients() {
        UUID first = payload().receiverId(), second = payload().receiverId();
        jdbc.execute("ALTER TABLE notification ADD CONSTRAINT reject_test_receiver CHECK (receiver_id <> '"
                + second + "'::uuid)");
        assertThatThrownBy(() -> save.savePage(NotificationType.FEED_CREATED, "FEED_CREATED:rollback",
                java.util.List.of(first, second), "제목", "본문", NotificationLevel.INFO))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
    }

    private com.codeit.otboo.domain.notification.event.NotificationCreateMessage<com.fasterxml.jackson.databind.JsonNode>
            sourceEvent(NotificationType type, UUID sourceId, Object payload) {
        return new NotificationCreateMessage<>(sourceId, 2, type, OffsetDateTime.now(), type.name() + ":" + sourceId,
                NotificationKafkaJson.mapper().valueToTree(payload));
    }

    @Test
    void workerLoadsCommentLikeFollowAndFeedWithoutProducerText() {
        UUID author = payload().receiverId(), receiver = payload().receiverId();
        UUID feed = UUID.randomUUID(), comment = UUID.randomUUID(), like = UUID.randomUUID(), follow = UUID.randomUUID();
        jdbc.update("INSERT INTO feed(id,user_id,content) VALUES (?,?,?)", feed, receiver, "피드 원문");
        jdbc.update("INSERT INTO feed_comment(id,feed_id,user_id,content) VALUES (?,?,?,?)", comment, feed, author, "실제 댓글 내용");
        jdbc.update("INSERT INTO feed_like(id,feed_id,user_id) VALUES (?,?,?)", like, feed, author);
        jdbc.update("INSERT INTO follow(id,follower_id,followee_id) VALUES (?,?,?)", follow, author, receiver);
        singleHandler.handle(sourceEvent(NotificationType.FEED_COMMENTED, comment,
                new com.codeit.otboo.domain.notification.event.FeedCommentNotificationPayload(comment)));
        singleHandler.handle(sourceEvent(NotificationType.FEED_LIKED, like,
                new com.codeit.otboo.domain.notification.event.FeedLikeNotificationPayload(like)));
        singleHandler.handle(sourceEvent(NotificationType.FOLLOWED, follow,
                new com.codeit.otboo.domain.notification.event.FollowNotificationPayload(follow)));
        assertThat(jdbc.queryForObject("SELECT content FROM notification WHERE type='FEED_COMMENTED'", String.class))
                .isEqualTo("실제 댓글 내용");
        assertThat(jdbc.queryForObject("SELECT content FROM notification WHERE type='FOLLOWED'", String.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT content FROM notification WHERE type='FEED_LIKED'", String.class))
                .isEqualTo("피드 원문");
        jdbc.update("UPDATE feed SET content=null WHERE id=?", feed);
        assertThat(sources.findLike(like).orElseThrow().content()).isEmpty();
        var result = fanOutHandler.handle(sourceEvent(NotificationType.FEED_CREATED, feed,
                new com.codeit.otboo.domain.notification.event.FeedNotificationCreateEvent(feed)));
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).receiverId()).isEqualTo(author);
        jdbc.update("UPDATE feed SET is_visible=false WHERE id=?", feed);
        assertThat(sources.findComment(comment)).isEmpty();
        assertThat(sources.findLike(like)).isEmpty();
        assertThat(sources.findFeed(feed)).isEmpty();
    }

    @Test
    void dmLookupEnforcesMembershipAndKafkaBroadcastContainsOriginalContent() throws Exception {
        UUID sender = payload().receiverId(), receiver = payload().receiverId(), outsider = payload().receiverId();
        UUID messageId = UUID.randomUUID(), room = UUID.randomUUID();
        jdbc.update("INSERT INTO direct_message VALUES (?,?,?,?,now())", messageId, room, sender, "실제 DM 내용");
        jdbc.update("INSERT INTO dm_room_member(dm_room_id,user_id,joined_at) VALUES (?,?,now()-interval '1 minute')", room, receiver);
        assertThat(sources.findDirectMessage(messageId, outsider)).isEmpty();
        assertThat(sources.findDirectMessage(messageId, sender)).isEmpty();
        var message = sourceEvent(NotificationType.DM_RECEIVED, messageId,
                new com.codeit.otboo.domain.notification.event.DirectMessageNotificationEvent(messageId, receiver));
        var props = KafkaTestUtils.consumerProps("source-" + UUID.randomUUID(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        var decoder = new JsonDeserializer<>(NotificationBroadcastEvent.class, NotificationKafkaJson.mapper(), false);
        try (var consumer = new KafkaConsumer<String, NotificationBroadcastEvent>(props, new StringDeserializer(), decoder)) {
            broker.consumeFromAnEmbeddedTopic(consumer, true, NotificationTopics.BROADCAST);
            publisher.publishCreate(receiver.toString(), message).get(10, TimeUnit.SECONDS);
            var notification = KafkaTestUtils.getSingleRecord(consumer, NotificationTopics.BROADCAST, Duration.ofSeconds(20))
                    .value().notification();
            assertThat(notification.content()).isEqualTo("실제 DM 내용");
            assertThat(notification.title()).isEqualTo("[DM] 작성자");
            assertThat(notification.receiverId()).isEqualTo(receiver);
        }
        jdbc.update("UPDATE dm_room_member SET left_at=now() WHERE user_id=?", receiver);
        assertThat(sources.findDirectMessage(messageId, receiver)).isEmpty();
        jdbc.update("UPDATE dm_room_member SET left_at=null, joined_at=now()+interval '1 minute' WHERE user_id=?", receiver);
        assertThat(sources.findDirectMessage(messageId, receiver)).isEmpty();
    }
}
