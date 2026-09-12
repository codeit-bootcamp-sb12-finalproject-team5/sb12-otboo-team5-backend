package com.codeit.otboo.api.notification.service;

import com.codeit.otboo.api.notification.dto.request.NotificationReadRequest;
import com.codeit.otboo.api.notification.dto.response.NotificationReadResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.Notification;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.domain.notification.repository.NotificationRepository;
import com.codeit.otboo.domain.notification.repository.NotificationQueryRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository repository;
    private final NotificationQueryRepository queryRepository;

    public NotificationReadResponse findNotifications(UUID receiverId, NotificationReadRequest request) {
        if ((request.cursor() == null) != (request.idAfter() == null)) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }
        OffsetDateTime cursor = null;
        if (request.cursor() != null) {
            try {
                cursor = OffsetDateTime.parse(request.cursor());
            } catch (DateTimeParseException e) {
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        var page = queryRepository.findUnread(receiverId, cursor, request.idAfter(), request.limit());
        return new NotificationReadResponse(page.data().stream().map(this::toDto).toList(),
                page.nextCursor(), (UUID) page.nextIdAfter(), page.hasNext(), page.totalCount(),
                page.sortBy(), page.sortDirection());
    }

    @Transactional
    public void readNotification(UUID receiverId, UUID notificationId) {
        var notification = repository.findByIdAndReceiverId(notificationId, receiverId)
                .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(notification.getId(), notification.getCreatedAt(),
                notification.getReceiver().getId(), notification.getTitle(),
                notification.getContent(), notification.getLevel());
    }
}
