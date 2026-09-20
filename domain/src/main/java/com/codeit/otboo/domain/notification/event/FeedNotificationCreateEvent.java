package com.codeit.otboo.domain.notification.event;

import java.util.UUID;

public record FeedNotificationCreateEvent(UUID feedId, UUID afterReceiverId) {
    public FeedNotificationCreateEvent(UUID feedId) {
        this(feedId, null);
    }
}
