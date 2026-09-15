package com.codeit.otboo.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import com.codeit.otboo.api.notification.event.NotificationEvents;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEventsTest {
    @Test
    void sourceEventsContainOnlyIdAndStableBusinessKey() {
        UUID source = UUID.randomUUID();
        var messages = List.of(NotificationEvents.feedLiked(source), NotificationEvents.commentCreated(source),
                NotificationEvents.followCreated(source));
        assertThat(messages).extracting(m -> m.type()).containsExactly(NotificationType.FEED_LIKED,
                NotificationType.FEED_COMMENTED, NotificationType.FOLLOWED);
        for (var message : messages) {
            assertThat(message.eventId()).isEqualTo(source);
            assertThat(message.schemaVersion()).isEqualTo(2);
            assertThat(message.deduplicationKey()).isEqualTo(message.type().name() + ":" + source);
            assertThat(message.payload().sourceId()).isEqualTo(source);
            assertThat(NotificationKafkaJson.mapper().valueToTree(message.payload()).size()).isEqualTo(1);
        }
    }

    @Test
    void dmSendsOnlyMessageAndReceiverAndFeedStartsWithoutCursor() {
        UUID id = UUID.randomUUID(), receiver = UUID.randomUUID();
        var dm = NotificationEvents.directMessageReceived(id, receiver);
        assertThat(dm.payload().messageId()).isEqualTo(id);
        assertThat(dm.payload().receiverId()).isEqualTo(receiver);
        assertThat(NotificationKafkaJson.mapper().valueToTree(dm.payload()).size()).isEqualTo(2);
        var feed = NotificationEvents.feedCreated(id);
        assertThat(feed.payload().feedId()).isEqualTo(id);
        assertThat(feed.payload().afterReceiverId()).isNull();
    }
}
