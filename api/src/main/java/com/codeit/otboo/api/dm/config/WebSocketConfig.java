package com.codeit.otboo.api.dm.config;

import com.codeit.otboo.api.common.security.StompAuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthenticationInterceptor stompAuthenticationInterceptor;

    // 클라이언트 송신 경로와 서버 발행 경로를 STOMP 브로커에 등록
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");    // (서버 -> 클라이언트 발행/구독) 서버 발행 경로
        registry.setApplicationDestinationPrefixes("/pub");     // (클라이언트 -> 서버) 송신 경로
    }

    // 같은 출처에서 사용할 STOMP WebSocket 연결 엔드포인트를 등록
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    // STOMP 수신 채널에 인증과 DM 구독 권한 검증 인터셉터를 등록
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthenticationInterceptor);
    }
}
