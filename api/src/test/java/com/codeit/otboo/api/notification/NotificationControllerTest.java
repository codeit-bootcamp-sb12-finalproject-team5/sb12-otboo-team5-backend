package com.codeit.otboo.api.notification;

import com.codeit.otboo.api.common.exception.GlobalExceptionHandler;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.notification.controller.*;
import com.codeit.otboo.api.notification.dto.response.NotificationReadResponse;
import com.codeit.otboo.api.notification.service.NotificationService;
import com.codeit.otboo.api.notification.service.NotificationSseService;
import com.codeit.otboo.api.notification.repository.SseEmitterRepository;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.web.bind.support.WebDataBinderFactory;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {
    private final NotificationService service = mock(NotificationService.class);
    private final NotificationSseService sse = new NotificationSseService(new SseEmitterRepository(), service,
            new DefaultListableBeanFactory().getBeanProvider(KafkaListenerEndpointRegistry.class), false);
    private final UUID user = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service), new SseController(sse))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter p) {
                        return p.getParameterType() == CustomUserDetails.class;
                    }
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer c,
                            NativeWebRequest r, WebDataBinderFactory f) {
                        return new CustomUserDetails(user, "test@example.com", "USER");
                    }
                }).build();
    }

    @AfterEach
    void cleanup() {
        sse.shutdown();
    }

    @Test
    void listMatchesContract() throws Exception {
        when(service.findNotifications(eq(user), any())).thenReturn(new NotificationReadResponse(
                List.of(), null, null, false, 0, "createdAt", "DESCENDING"));
        mvc.perform(get("/api/notifications").param("limit", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("data").isArray())
                .andExpect(jsonPath("totalCount").value(0))
                .andExpect(jsonPath("sortDirection").value("DESCENDING"));
    }

    @Test
    void invalidInputsReturnCommon400() throws Exception {
        for (String limit : List.of("0", "101", "abc")) {
            mvc.perform(get("/api/notifications").param("limit", limit))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("exceptionName").exists());
        }
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/notifications/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("message").exists());
        verifyNoInteractions(service);
    }

    @Test
    void deleteReturns204() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(delete("/api/notifications/" + id)).andExpect(status().isNoContent());
        verify(service).readNotification(user, id);
    }

    @Test
    void sseStartsStreamAndAcceptsLastEventId() throws Exception {
        mvc.perform(get("/api/sse").param("lastEventId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(":connected")));
    }

    // 커서가 깨졌다고 연결까지 막으면 알림이 통째로 끊긴다.
    @Test
    void brokenLastEventIdStillOpensTheStream() throws Exception {
        mvc.perform(get("/api/sse").param("lastEventId", "invalid"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(":connected")));
        verify(service, never()).findMissed(any(), any(), anyInt());
    }

    @Test
    void reconnectResendsNotificationsMissedWhileDisconnected() throws Exception {
        var lastSeen = UUID.randomUUID();
        var missedFirst = UUID.randomUUID();
        var missedSecond = UUID.randomUUID();
        when(service.findMissed(eq(user), eq(lastSeen), anyInt())).thenReturn(List.of(
                new NotificationDto(missedFirst, OffsetDateTime.now(), user,
                        "먼저 온 알림", "내용1", NotificationLevel.INFO),
                new NotificationDto(missedSecond, OffsetDateTime.now(), user,
                        "나중에 온 알림", "내용2", NotificationLevel.INFO)));

        var result = mvc.perform(get("/api/sse").param("lastEventId", lastSeen.toString())).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("id:" + missedFirst, "id:" + missedSecond, "event:notifications");
        assertThat(body.indexOf("id:" + missedFirst)).isLessThan(body.indexOf("id:" + missedSecond));
        assertThat(body.indexOf(":connected")).isLessThan(body.indexOf("id:" + missedFirst));
    }

    // 사용자당 연결은 1개만 유지하므로, 나중에 연결한 쪽만 알림을 받는다.
    @Test
    void broadcastsToTheNewestUserConnectionUsingFrontendEventContract() throws Exception {
        var replaced = mvc.perform(get("/api/sse")).andReturn();
        var current = mvc.perform(get("/api/sse")).andReturn();
        var id = UUID.randomUUID();
        sse.publish(new NotificationDto(id, OffsetDateTime.now(), user,
                "role changed", "ADMIN", NotificationLevel.INFO));

        String body = current.getResponse().getContentAsString();
        assertThat(body).contains("event:notifications", "id:" + id,
                "\"receiverId\":\"" + user);
        assertThat(body.indexOf(":connected")).isLessThan(body.indexOf("event:notifications"));

        assertThat(replaced.getResponse().getContentAsString())
                .contains(":connected")
                .doesNotContain("event:notifications");
    }

    @Test
    void heartbeatReachesConnectedClients() throws Exception {
        var result = mvc.perform(get("/api/sse")).andReturn();
        sse.heartbeat();
        assertThat(result.getResponse().getContentAsString()).contains(":connected", ":heartbeat");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsSubscriptionBeforeKafkaConsumerIsAssigned() {
        org.springframework.beans.factory.ObjectProvider<org.springframework.kafka.config.KafkaListenerEndpointRegistry>
                registries = mock(org.springframework.beans.factory.ObjectProvider.class);
        var gated = new NotificationSseService(new SseEmitterRepository(), service, registries, true);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> gated.subscribe(user, null))
                    .isInstanceOfSatisfying(
                            com.codeit.otboo.domain.notification.exception.NotificationException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(
                                    com.codeit.otboo.domain.common.exception.ErrorCode.NOTIFICATION_STREAM_UNAVAILABLE));
        } finally {
            gated.shutdown();
        }
    }
}
