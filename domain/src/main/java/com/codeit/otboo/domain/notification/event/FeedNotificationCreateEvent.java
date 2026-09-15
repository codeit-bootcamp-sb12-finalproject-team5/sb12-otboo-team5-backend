package com.codeit.otboo.domain.notification.event;

import java.util.UUID;

/** 제목·본문·작성자는 worker가 조회한다. 커서는 후속 페이지에서만 사용한다. */
public record FeedNotificationCreateEvent(UUID feedId, UUID afterReceiverId) {
    public FeedNotificationCreateEvent(UUID feedId) {
        this(feedId, null);
    }
}
