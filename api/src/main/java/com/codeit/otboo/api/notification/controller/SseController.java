package com.codeit.otboo.api.notification.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.notification.service.NotificationSseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class SseController {
    private final NotificationSseService service;

    @GetMapping(value = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(name = "LastEventId", required = false) UUID lastEventId
    ) {
        // 이벤트 이력을 보관하지 않는다. 재연결 시 누락 알림은 목록 API로 조회한다.
        return ResponseEntity.ok().header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(service.subscribe(principal.getUserId()));
    }
}
