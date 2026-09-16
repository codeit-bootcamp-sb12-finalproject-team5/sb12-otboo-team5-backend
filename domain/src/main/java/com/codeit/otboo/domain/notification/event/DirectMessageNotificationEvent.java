package com.codeit.otboo.domain.notification.event;

import java.util.UUID;

public record DirectMessageNotificationEvent(
	UUID messageId,
	UUID receiverId
) {
}
