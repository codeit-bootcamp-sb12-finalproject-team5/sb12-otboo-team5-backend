package com.codeit.otboo.api.dm.controller;

import com.codeit.otboo.api.dm.dto.CreateDmRoomRequest;
import com.codeit.otboo.api.dm.dto.DmRoomResponse;
import com.codeit.otboo.api.dm.dto.DmRoomListResponse;
import com.codeit.otboo.api.dm.dto.DirectMessageListResponse;
import com.codeit.otboo.api.dm.dto.DirectMessageReadRequest;
import com.codeit.otboo.api.dm.service.DirectMessageReadService;
import com.codeit.otboo.api.dm.service.DirectMessageListService;
import com.codeit.otboo.api.dm.service.DmRoomListService;
import com.codeit.otboo.api.dm.service.DmRoomService;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import java.util.UUID;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DmRoomController {

    private final DmRoomService dmRoomService;
    private final DmRoomListService dmRoomListService;
    private final DirectMessageListService directMessageListService;
    private final DirectMessageReadService directMessageReadService;

    // TODO: 공통 성공 응답 규격이 정해지면 ResponseEntity body를 해당 형식으로 통일
    @GetMapping
    public ResponseEntity<DmRoomListResponse> getDmRooms(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String cursor
    ) {
        return ResponseEntity.ok(dmRoomListService.getDmRooms(userDetails.getUserId(), cursor));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<DirectMessageListResponse> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID roomId,
            @RequestParam(required = false) UUID cursor
    ) {
        return ResponseEntity.ok(directMessageListService.getMessages(userDetails.getUserId(), roomId, cursor));
    }

    @PatchMapping("/{roomId}/read")
    public ResponseEntity<Void> markMessagesAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID roomId,
            @Valid @RequestBody DirectMessageReadRequest request
    ) {
        directMessageReadService.markAsRead(userDetails.getUserId(), roomId, request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }

    // TODO: 공통 성공 응답 규격이 정해지면 ResponseEntity body를 해당 형식으로 통일
    @PostMapping("/rooms")
    public ResponseEntity<DmRoomResponse> createOrGet(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateDmRoomRequest request
    ) {
        return ResponseEntity.ok(
                dmRoomService.createOrGet(userDetails.getUserId(), request.receiverId())
        );
    }
}
