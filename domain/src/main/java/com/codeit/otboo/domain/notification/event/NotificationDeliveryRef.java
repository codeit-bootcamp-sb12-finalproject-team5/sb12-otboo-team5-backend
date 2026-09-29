package com.codeit.otboo.domain.notification.event;

import java.util.UUID;

public record NotificationDeliveryRef(
	UUID notificationId,
	UUID receiverId
) {
}
