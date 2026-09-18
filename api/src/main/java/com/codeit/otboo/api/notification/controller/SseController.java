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
            // EventSource 폴리필은 재연결할 때 마지막으로 받은 이벤트 id를 이 이름으로 붙인다.
            @RequestParam(name = "lastEventId", required = false) String lastEventId
    ) {
        return ResponseEntity.ok().header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(service.subscribe(principal.getUserId(), parseCursor(lastEventId)));
    }

    // 커서 하나 때문에 재연결이 실패하면 알림이 통째로 끊긴다. 형식이 잘못되면 버리고 연결만 이어간다.
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
