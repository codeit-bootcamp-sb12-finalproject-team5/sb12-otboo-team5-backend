package com.codeit.otboo.worker.notification.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.dto.NotificationContent;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.*;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.codeit.otboo.worker.notification.repository.NotificationSourceRepository;
import com.codeit.otboo.worker.notification.service.NotificationSaveService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SingleNotificationHandlerTest {
    private final NotificationSaveService save = mock(NotificationSaveService.class);
    private final NotificationSourceRepository sources = mock(NotificationSourceRepository.class);
    private final SingleNotificationHandler handler = new SingleNotificationHandler(save, sources);
    private final UUID id = UUID.randomUUID(), receiver = UUID.randomUUID();

    private NotificationCreateMessage<JsonNode> event(NotificationType type, Object payload) {
        return new NotificationCreateMessage<>(id, 2, type, OffsetDateTime.now(), type.name() + ":" + id,
                NotificationKafkaJson.mapper().valueToTree(payload));
    }

    @Test
    void roleIsAssembledFromChangeSnapshot() {
        handler.handle(event(NotificationType.ROLE_CHANGED, new RoleChangedNotificationEvent(receiver, UserRole.ADMIN)));
        verify(save).save(eq(NotificationType.ROLE_CHANGED), anyString(), argThat(v ->
                v.receiverId().equals(receiver) && v.content().contains("관리자")));
        verifyNoInteractions(sources);
    }

    @ParameterizedTest
    @ValueSource(strings = {"댓글 원문", "", "   "})
    void commentContentIncludingEmptyStringIsPreserved(String body) {
        when(sources.findComment(id)).thenReturn(Optional.of(new NotificationSourceRepository.Source(receiver, "작성자", body)));
        handler.handle(event(NotificationType.FEED_COMMENTED, new FeedCommentNotificationPayload(id)));
        verify(save).save(eq(NotificationType.FEED_COMMENTED), anyString(), eq(new NotificationContent(
                receiver, "작성자님이 댓글을 달았어요.", body, NotificationLevel.INFO)));
    }

    @Test
    void followUsesEmptyBodyAndLikeUsesFeedContent() {
        var source = new NotificationSourceRepository.Source(receiver, "작성자", "");
        when(sources.findFollow(id)).thenReturn(Optional.of(source));
        when(sources.findLike(id)).thenReturn(Optional.of(
                new NotificationSourceRepository.Source(receiver, "작성자", "피드 원문")));
        handler.handle(event(NotificationType.FOLLOWED, new FollowNotificationPayload(id)));
        handler.handle(event(NotificationType.FEED_LIKED, new FeedLikeNotificationPayload(id)));
        verify(save).save(eq(NotificationType.FOLLOWED), anyString(), argThat(v -> v.content().isEmpty()));
        verify(save).save(eq(NotificationType.FEED_LIKED), anyString(), eq(new NotificationContent(
                receiver, "작성자님이 내 피드를 좋아합니다.", "피드 원문", NotificationLevel.INFO)));
    }

    @Test
    void dmQueriesMessageAndReceiverAndLimitsPreviewByUnicodeCodePoints() {
        String body = "😀".repeat(1001);
        when(sources.findDirectMessage(id, receiver)).thenReturn(Optional.of(
                new NotificationSourceRepository.Source(receiver, "발신자", body)));
        handler.handle(event(NotificationType.DM_RECEIVED, new DirectMessageNotificationEvent(id, receiver)));
        verify(save).save(eq(NotificationType.DM_RECEIVED), anyString(), eq(new NotificationContent(
                receiver, "[DM] 발신자", "😀".repeat(1000), NotificationLevel.INFO)));
    }

    @Test
    void missingSourceOrUnauthorizedDmDoesNotSave() {
        assertThatThrownBy(() -> handler.handle(event(NotificationType.DM_RECEIVED,
                new DirectMessageNotificationEvent(id, receiver))))
                .isInstanceOfSatisfying(NotificationException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_SOURCE_NOT_FOUND));
        verifyNoInteractions(save);
    }

    @Test
    void oldPreformattedPayloadIsRejected() {
        assertThatThrownBy(() -> handler.handle(event(NotificationType.ROLE_CHANGED,
                new NotificationContent(receiver, "임의 제목", "임의 본문", NotificationLevel.INFO))))
                .isInstanceOf(NotificationException.class);
        verifyNoInteractions(save, sources);
    }

    @Test
    void payloadForAnotherTypeIsRejectedBeforeLookup() {
        assertThatThrownBy(() -> handler.handle(event(NotificationType.FEED_COMMENTED,
                new FeedLikeNotificationPayload(id)))).isInstanceOf(NotificationException.class);
        verifyNoInteractions(save, sources);
    }

    @Test
    void invalidOrMissingSourceIdIsRejectedBeforeLookup() {
        assertThatThrownBy(() -> handler.handle(event(NotificationType.FEED_COMMENTED,
                new FeedCommentNotificationPayload(null)))).isInstanceOf(NotificationException.class);
        assertThatThrownBy(() -> handler.handle(event(NotificationType.FEED_COMMENTED,
                new FeedCommentNotificationPayload(UUID.randomUUID())))).isInstanceOf(NotificationException.class);
        verifyNoInteractions(save, sources);
    }
}
