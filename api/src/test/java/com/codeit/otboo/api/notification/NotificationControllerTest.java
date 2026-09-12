package com.codeit.otboo.api.notification;

import com.codeit.otboo.api.common.exception.GlobalExceptionHandler;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.notification.controller.*;
import com.codeit.otboo.api.notification.dto.response.NotificationReadResponse;
import com.codeit.otboo.api.notification.service.NotificationService;
import com.codeit.otboo.api.notification.service.NotificationSseService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
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
    private final NotificationSseService sse = new NotificationSseService();
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
    void cleanup() { sse.shutdown(); }

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
        mvc.perform(get("/api/sse").param("LastEventId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(":connected")));
        mvc.perform(get("/api/sse").param("LastEventId", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
