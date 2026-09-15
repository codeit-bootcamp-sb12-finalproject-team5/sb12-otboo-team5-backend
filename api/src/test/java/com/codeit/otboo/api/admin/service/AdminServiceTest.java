package com.codeit.otboo.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.otboo.api.admin.dto.UserLockUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserRoleUpdateRequest;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.RefreshTokenRepository;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.SingleNotificationCreateEvent;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AdminServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            mock(RefreshTokenRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AdminService adminService =
            new AdminService(userRepository, refreshTokenRepository, eventPublisher);

    private User user(UserRole role, boolean locked, int tokenVersion) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("woody@otboo.io")
                .name("우디")
                .password("encoded")
                .role(role)
                .locked(locked)
                .tokenVersion(tokenVersion)
                .build();
    }

    /** 권한 변경 시 tokenVersion 이 증가해 기존 토큰이 무효화되는지 확인합니다. */
    @Test
    void invalidatesSessionWhenRoleChanged() {
        UUID userId = UUID.randomUUID();
        User target = user(UserRole.USER, false, 2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        UserDto result = adminService.updateRole(
                userId, new UserRoleUpdateRequest(UserRole.ADMIN));

        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(target.getTokenVersion()).isEqualTo(3);
        verify(refreshTokenRepository).deleteByUser(target);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        NotificationCreateMessage<?> message = (NotificationCreateMessage<?>) event.getValue();
        assertThat(message.type()).isEqualTo(NotificationType.ROLE_CHANGED);
        assertThat(message.eventId().version()).isEqualTo(7);
        assertThat(message.deduplicationKey()).isEqualTo("ROLE_CHANGED:" + message.eventId());
        SingleNotificationCreateEvent payload = (SingleNotificationCreateEvent) message.payload();
        assertThat(payload.receiverId()).isEqualTo(target.getId());
        assertThat(payload.content()).contains("관리자");
    }

    @Test
    void doesNotNotifyWhenRoleIsUnchanged() {
        User target = user(UserRole.ADMIN, false, 2);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        adminService.updateRole(target.getId(), new UserRoleUpdateRequest(UserRole.ADMIN));

        verifyNoInteractions(eventPublisher);
        // 기존 세션 무효화 동작은 유지한다.
        assertThat(target.getTokenVersion()).isEqualTo(3);
    }

    /** 계정 잠금 시 tokenVersion 이 증가해 즉시 로그아웃되는지 확인합니다. */
    @Test
    void invalidatesSessionWhenAccountLocked() {
        UUID userId = UUID.randomUUID();
        User target = user(UserRole.USER, false, 1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        UserDto result = adminService.updateLock(userId, new UserLockUpdateRequest(true));

        assertThat(result.locked()).isTrue();
        assertThat(target.getTokenVersion()).isEqualTo(2);
        verify(refreshTokenRepository).deleteByUser(target);
    }

    /** 잠금 해제 시에는 기존 세션을 무효화하지 않는지 확인합니다. */
    @Test
    void keepsSessionWhenAccountUnlocked() {
        UUID userId = UUID.randomUUID();
        User target = user(UserRole.USER, true, 5);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        UserDto result = adminService.updateLock(userId, new UserLockUpdateRequest(false));

        assertThat(result.locked()).isFalse();
        assertThat(target.getTokenVersion()).isEqualTo(5);
        verify(refreshTokenRepository, never()).deleteByUser(target);
    }

    /** 존재하지 않는 사용자의 권한 변경은 거부하는지 확인합니다. */
    @Test
    void rejectsRoleChangeForUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateRole(
                userId, new UserRoleUpdateRequest(UserRole.ADMIN)))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(eventPublisher);
    }

    /** 존재하지 않는 사용자의 잠금 변경은 거부하는지 확인합니다. */
    @Test
    void rejectsLockChangeForUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateLock(
                userId, new UserLockUpdateRequest(true)))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
