package com.codeit.otboo.domain.notification.event;

import java.util.UUID;

public record FeedCommentNotificationPayload(
    UUID commentId
) {
}
