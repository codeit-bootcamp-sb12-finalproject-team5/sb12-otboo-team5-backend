package com.codeit.otboo.domain.notification.event;

import java.util.UUID;
import java.util.List;

public record FeedNotificationCreateEvent(
        UUID feedId, UUID afterReceiverId, List<UUID> receiverIds
) {
    public FeedNotificationCreateEvent(UUID feedId) {
        this(feedId, null, null);
    }

    public FeedNotificationCreateEvent(UUID feedId, UUID afterReceiverId) {
        this(feedId, afterReceiverId, null);
    }
}
