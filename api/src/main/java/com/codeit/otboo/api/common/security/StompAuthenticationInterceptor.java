package com.codeit.otboo.api.common.security;

import com.codeit.otboo.domain.dm.entity.DmRoom;
import com.codeit.otboo.domain.dm.repository.DmRoomMemberRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

//todo: 예외 처리 필요
@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DIRECT_MESSAGE_DESTINATION_PREFIX = "/sub/direct-messages_";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final DmRoomRepository dmRoomRepository;
    private final DmRoomMemberRepository dmRoomMemberRepository;

    // STOMP CONNECT의 JWT를 검증하고 이후 메시지에서 사용할 인증 Principal을 등록
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader(AUTHORIZATION_HEADER)));
            return message;
        }

        if (!StompCommand.SEND.equals(accessor.getCommand())
            && !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        Authentication authentication = getAuthentication(accessor);
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateSubscription(accessor.getDestination(), getUserId(authentication));
        }

        return message;
    }

    // Authorization 헤더의 Access Token과 현재 사용자 상태를 검증
    private Authentication authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw invalidTokenException();
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!jwtTokenProvider.validate(token)) {
            throw invalidTokenException();
        }

        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            UUID userId = UUID.fromString(claims.getSubject());
            Integer tokenVersion = claims.get("tokenVersion", Integer.class);
            User user = userRepository.findById(userId)
                .orElseThrow(this::invalidTokenException);

            if (!user.getTokenVersion().equals(tokenVersion) || Boolean.TRUE.equals(user.getLocked())) {
                throw invalidTokenException();
            }

            CustomUserDetails principal = new CustomUserDetails(
                user.getId(), user.getEmail(), user.getRole().name());
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        } catch (IllegalArgumentException e) {
            throw invalidTokenException();
        }
    }

    // CONNECT 인증 없이 STOMP 메시지를 보내거나 구독하는 요청을 차단
    private Authentication getAuthentication(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
            && authentication.getPrincipal() instanceof CustomUserDetails) {
            return authentication;
        }
        throw accessDeniedException();
    }

    // DM 구독 경로의 dmKey에 현재 사용자가 활성 멤버로 참여했는지 확인
    private void validateSubscription(String destination, UUID currentUserId) {
        if (destination == null || !destination.startsWith(DIRECT_MESSAGE_DESTINATION_PREFIX)) {
            return;
        }

        String dmKey = destination.substring(DIRECT_MESSAGE_DESTINATION_PREFIX.length());
        DmRoom room = dmRoomRepository.findByDmKey(dmKey)
            .orElseThrow(this::accessDeniedException);

        if (!dmRoomMemberRepository.existsByDmRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), currentUserId)) {
            throw accessDeniedException();
        }
    }

    // 인증 객체에서 현재 사용자 식별자를 추출한다.
    private UUID getUserId(Authentication authentication) {
        return ((CustomUserDetails) authentication.getPrincipal()).getUserId();
    }

    // 기존 공통 오류 코드로 토큰 인증 실패 예외를 생성한다.
    private AccessDeniedException invalidTokenException() {
        return new AccessDeniedException(ErrorCode.INVALID_TOKEN.getMessage());
    }

    // 기존 공통 오류 코드로 STOMP 접근 권한 예외를 생성한다.
    private AccessDeniedException accessDeniedException() {
        return new AccessDeniedException(ErrorCode.ACCESS_DENIED.getMessage());
    }
}
