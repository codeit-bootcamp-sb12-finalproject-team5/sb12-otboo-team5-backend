package com.codeit.otboo.api.notification.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.notification.dto.request.NotificationReadRequest;
import com.codeit.otboo.api.notification.dto.response.NotificationReadResponse;
import com.codeit.otboo.api.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public ResponseEntity<NotificationReadResponse> findNotifications(
            @AuthenticationPrincipal CustomUserDetails principal,
            @ModelAttribute @Valid NotificationReadRequest request
    ) {
        return ResponseEntity.ok(service.findNotifications(principal.getUserId(), request));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> readNotification(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("notificationId") UUID notificationId
    ) {
        service.readNotification(principal.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
