package com.codeit.otboo.worker.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.dto.NotificationContent;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.worker.notification.repository.NotificationInsertRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationSaveServiceTest {
    @Test
    void missingReceiverUsesCommonUserErrorWithoutInsert() {
        var repository = mock(NotificationInsertRepository.class);
        var service = new NotificationSaveService(repository);
        var receiverId = UUID.randomUUID();
        var payload = new NotificationContent(receiverId, "title", "content", NotificationLevel.INFO);
        assertThatThrownBy(() -> service.save(NotificationType.ROLE_CHANGED, "ROLE_CHANGED:1", payload))
                .isInstanceOfSatisfying(NotificationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                    assertThat(exception.getDetails()).containsEntry("receiverId", receiverId);
                });
        verify(repository, never()).insert(any(), any(), any());
    }
}
