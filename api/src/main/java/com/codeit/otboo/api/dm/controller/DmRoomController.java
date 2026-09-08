package com.codeit.otboo.api.dm.controller;

import com.codeit.otboo.api.dm.dto.CreateDmRoomRequest;
import com.codeit.otboo.api.dm.dto.DmRoomResponse;
import com.codeit.otboo.api.dm.service.DmRoomService;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/direct-messages/rooms")
@RequiredArgsConstructor
public class DmRoomController {

    private final DmRoomService dmRoomService;

    // TODO: 공통 성공 응답 규격이 정해지면 ResponseEntity body를 해당 형식으로 통일
    @PostMapping
    public ResponseEntity<DmRoomResponse> createOrGet(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateDmRoomRequest request
    ) {
        return ResponseEntity.ok(
                dmRoomService.createOrGet(userDetails.getUserId(), request.receiverId())
        );
    }
}
