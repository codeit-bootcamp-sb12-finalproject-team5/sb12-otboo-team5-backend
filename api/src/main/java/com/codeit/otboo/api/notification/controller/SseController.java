package com.codeit.otboo.api.notification.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.notification.service.NotificationSseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SseController {
    private final NotificationSseService service;

    @GetMapping(value = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(name = "lastEventId", required = false) String lastEventId
    ) {
        return ResponseEntity.ok().header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(service.subscribe(principal.getUserId(), parseCursor(lastEventId)));
    }

    private UUID parseCursor(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(lastEventId);
        } catch (IllegalArgumentException exception) {
            log.debug("[SSE] 형식이 잘못된 lastEventId를 무시한다 value={}", lastEventId);
            return null;
        }
    }
}
