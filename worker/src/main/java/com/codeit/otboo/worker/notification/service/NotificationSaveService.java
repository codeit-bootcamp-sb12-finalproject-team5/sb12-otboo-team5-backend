package com.codeit.otboo.worker.notification.service;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.worker.notification.repository.NotificationInsertRepository;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.dto.NotificationContent;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSaveService {
    private final NotificationInsertRepository repository;

    @Transactional
    public Optional<NotificationDto> save(
        NotificationType type, String key,
        NotificationContent payload
    ) {
        if (!repository.receiverExists(payload.receiverId())) {
            throw new NotificationException(ErrorCode.USER_NOT_FOUND)
                    .addDetail("receiverId", payload.receiverId());
        }

        return repository.insert(type, key, payload);
    }

    @Transactional
    public List<NotificationDto> savePage(NotificationType type, String key, List<UUID> receivers,
            String title, String content, NotificationLevel level) {
        List<NotificationDto> saved = new ArrayList<>();
        for (UUID receiver : receivers) {
            repository.insert(
                type, key, new NotificationContent(receiver, title, content, level))
                    .ifPresent(saved::add);
        }

        return List.copyOf(saved);
    }
}
