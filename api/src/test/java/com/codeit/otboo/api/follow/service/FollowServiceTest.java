package com.codeit.otboo.api.follow.service;

import com.codeit.otboo.api.follow.dto.request.FollowCreateRequest;
import com.codeit.otboo.domain.follow.entity.Follow;
import com.codeit.otboo.domain.follow.exception.FollowException;
import com.codeit.otboo.domain.follow.repository.FollowRepository;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.FollowNotificationPayload;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private FollowService followService;

    @Test
    void publishesFollowNotificationAfterSavingTheFollow() {
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();
        User follower = User.builder().id(followerId).name("follower").build();
        User followee = User.builder().id(followeeId).name("followee").build();

        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
        when(followRepository.existsByFollowerAndFollowee(follower, followee)).thenReturn(false);
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation ->
            Follow.builder().id(followId).follower(follower).followee(followee).build());
        when(profileRepository.findProfileImagesByUserIds(List.of(followerId, followeeId)))
            .thenReturn(List.of());

        var response = followService.createFollow(new FollowCreateRequest(followeeId, followerId));

        assertThat(response.id()).isEqualTo(followId);

        ArgumentCaptor<NotificationCreateMessage<FollowNotificationPayload>> captor =
            ArgumentCaptor.forClass(NotificationCreateMessage.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.FOLLOWED);
        assertThat(captor.getValue().payload().followId()).isEqualTo(followId);
        assertThat(captor.getValue().deduplicationKey()).isEqualTo("FOLLOWED:" + followId);
    }

    @Test
    void doesNotPublishWhenTheFollowAlreadyExists() {
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        User follower = User.builder().id(followerId).name("follower").build();
        User followee = User.builder().id(followeeId).name("followee").build();

        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
        when(followRepository.existsByFollowerAndFollowee(follower, followee)).thenReturn(true);

        assertThatThrownBy(() ->
            followService.createFollow(new FollowCreateRequest(followeeId, followerId)))
            .isInstanceOf(FollowException.class);

        verify(followRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createFollowRunsInATransactionSoThePublishedEventIsDelivered() throws Exception {
        Method method = FollowService.class.getMethod("createFollow", FollowCreateRequest.class);

        assertThat(method.isAnnotationPresent(Transactional.class)
            || FollowService.class.isAnnotationPresent(Transactional.class))
            .as("createFollow에 @Transactional이 없으면 팔로우 알림이 발행되지 않고 사라진다")
            .isTrue();
    }

    @Test
    void doesNotPublishWhenFollowingYourself() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).name("me").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(followRepository.existsByFollowerAndFollowee(user, user)).thenReturn(false);

        assertThatThrownBy(() ->
            followService.createFollow(new FollowCreateRequest(userId, userId)))
            .isInstanceOf(FollowException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
