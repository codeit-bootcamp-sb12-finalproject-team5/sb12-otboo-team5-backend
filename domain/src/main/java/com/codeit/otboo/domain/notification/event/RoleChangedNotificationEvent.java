package com.codeit.otboo.domain.notification.event;

import com.codeit.otboo.domain.user.entity.UserRole;
import java.util.UUID;

public record RoleChangedNotificationEvent(
	UUID receiverId,
	UserRole role
) {
}
