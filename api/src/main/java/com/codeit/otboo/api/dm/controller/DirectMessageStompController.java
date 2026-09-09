package com.codeit.otboo.api.dm.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.dm.dto.DirectMessageResponse;
import com.codeit.otboo.api.dm.dto.SendDirectMessageRequest;
import com.codeit.otboo.api.dm.service.DirectMessageService;
import com.codeit.otboo.domain.dm.entity.DmRoom;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageStompController {

    private static final String DIRECT_MESSAGE_DESTINATION_PREFIX = "/sub/direct-messages_";

    private final DirectMessageService directMessageService;
    private final DmRoomRepository dmRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // STOMP DM 전송 요청을 저장한 뒤 해당 방의 dmKey 구독 경로로 메시지를 발행한다.
    @MessageMapping("/direct-messages_send")
    public void sendMessage(@Valid @Payload SendDirectMessageRequest request, Principal principal) {
        DirectMessageResponse response = directMessageService.sendMessage(
            getCurrentUserId(principal), request.roomId(), request.receiverId(), request.content());

        DmRoom room = dmRoomRepository.findById(response.roomId())
            .orElseThrow(() -> new IllegalStateException("저장된 DM 방을 찾을 수 없습니다."));

        messagingTemplate.convertAndSend(DIRECT_MESSAGE_DESTINATION_PREFIX + room.getDmKey(), response);
    }

    private UUID getCurrentUserId(Principal principal) {
        if (principal instanceof Authentication authentication
            && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }

        throw new AccessDeniedException("STOMP 인증이 필요합니다.");
    }
}
