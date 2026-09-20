package com.codeit.otboo.worker.notification.handler;

import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public interface NotificationRequestHandler {
    Set<NotificationType> supportedTypes();
    NotificationHandlingResult handle(NotificationCreateMessage<JsonNode> message);
}
