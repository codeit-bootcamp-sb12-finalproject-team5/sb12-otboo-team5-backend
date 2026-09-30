package com.codeit.otboo.api.notification.event;

import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.DirectMessageNotificationEvent;
import com.codeit.otboo.domain.notification.event.FeedNotificationCreateEvent;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.FeedLikeNotificationPayload;
import com.codeit.otboo.domain.notification.event.FeedCommentNotificationPayload;
import com.codeit.otboo.domain.notification.event.FollowNotificationPayload;
import com.codeit.otboo.domain.notification.event.RoleChangedNotificationEvent;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.fasterxml.uuid.Generators;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class NotificationEvents {
    private NotificationEvents() {
    }

    public static NotificationCreateMessage<RoleChangedNotificationEvent> roleChanged(
            UUID receiverId, UserRole role) {
        return create(
            NotificationType.ROLE_CHANGED,
            Generators.timeBasedEpochGenerator().generate(),
            new RoleChangedNotificationEvent(receiverId, role)
        );
    }

    public static NotificationCreateMessage<FeedLikeNotificationPayload> feedLiked(UUID likeId) {
        return create(
            NotificationType.FEED_LIKED,
            likeId,
            new FeedLikeNotificationPayload(likeId)
        );
    }

    public static NotificationCreateMessage<FeedCommentNotificationPayload> commentCreated(UUID commentId) {
        return create(
            NotificationType.FEED_COMMENTED,
            commentId,
            new FeedCommentNotificationPayload(commentId)
        );
    }

    public static NotificationCreateMessage<FollowNotificationPayload> followCreated(UUID followId) {
        return create(
            NotificationType.FOLLOWED,
            followId,
            new FollowNotificationPayload(followId)
        );
    }

    public static NotificationCreateMessage<DirectMessageNotificationEvent> directMessageReceived(
            UUID messageId, UUID receiverId) {
        return create(
            NotificationType.DM_RECEIVED,
            messageId,
            new DirectMessageNotificationEvent(messageId, receiverId)
        );
    }

    public static NotificationCreateMessage<FeedNotificationCreateEvent> feedCreated(UUID feedId) {
        return create(
            NotificationType.FEED_CREATED,
            feedId,
            new FeedNotificationCreateEvent(feedId)
        );
    }

    private static <T> NotificationCreateMessage<T> create(NotificationType type, UUID sourceId, T payload) {
        return new NotificationCreateMessage<>(
            sourceId,
            NotificationCreateMessage.CURRENT_SCHEMA_VERSION,
            type,
            OffsetDateTime.now(),
            type.name() + ":" + sourceId,
            payload
        );
    }
}
