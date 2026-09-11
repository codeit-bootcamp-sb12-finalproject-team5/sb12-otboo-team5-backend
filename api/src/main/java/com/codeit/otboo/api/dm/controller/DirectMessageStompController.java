package com.codeit.otboo.api.dm.controller;

import com.codeit.otboo.api.common.dto.ErrorResponse;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.dm.dto.DirectMessageResponse;
import com.codeit.otboo.api.dm.dto.SendDirectMessageRequest;
import com.codeit.otboo.api.dm.service.DirectMessageService;
import com.codeit.otboo.domain.dm.entity.DmRoom;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import jakarta.validation.Valid;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.FieldError;

@Controller
@Slf4j
@RequiredArgsConstructor
public class DirectMessageStompController {

    private static final String DIRECT_MESSAGE_DESTINATION_PREFIX = "/sub/direct-messages_";
    private static final String USER_DM_LIST_DESTINATION_PREFIX = "/sub/users_";

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
        messagingTemplate.convertAndSend(
            USER_DM_LIST_DESTINATION_PREFIX + request.receiverId() + "/dm-list", response);
    }

    // DM 비즈니스 예외를 현재 STOMP 세션에 REST 공통 오류 응답 형식으로 전달한다.
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(value = "/sub/errors", broadcast = false)
    public ErrorResponse handleBusinessException(BusinessException exception) {
        log.warn("[{}] {} details={}",
            exception.getErrorCode().name(), exception.getMessage(), exception.getDetails());

        return ErrorResponse.of(exception);
    }

    // STOMP Payload Bean Validation 실패를 현재 세션에 REST 공통 오류 응답 형식으로 전달한다.
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(value = "/sub/errors", broadcast = false)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("[VALIDATION] {}", details);
        return ErrorResponse.of(
            exception.getClass().getSimpleName(), ErrorCode.INVALID_INPUT_VALUE.getMessage(), details);
    }

    private UUID getCurrentUserId(Principal principal) {
        if (principal instanceof Authentication authentication
            && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }

        throw new AccessDeniedException(ErrorCode.ACCESS_DENIED.getMessage());
    }
}
