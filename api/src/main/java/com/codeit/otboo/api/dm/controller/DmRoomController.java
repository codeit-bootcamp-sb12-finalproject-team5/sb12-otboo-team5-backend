package com.codeit.otboo.api.dm.controller;

import com.codeit.otboo.api.dm.dto.CreateDmRoomRequest;
import com.codeit.otboo.api.dm.dto.DmRoomResponse;
import com.codeit.otboo.api.dm.service.DmRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
            @Valid @RequestBody CreateDmRoomRequest request
    ) {
        // TODO: 인증 기능 구현 후 현재 로그인 사용자 ID를 인증 객체에서 가져오도록 변경(현재는 request body에서 가져옴)
        return ResponseEntity.ok(dmRoomService.createOrGet(request));
    }
}