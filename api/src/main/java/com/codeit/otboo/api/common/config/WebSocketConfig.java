package com.codeit.otboo.api.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 클라이언트 송신 경로와 서버 발행 경로를 STOMP 브로커에 등록한다.
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    // 같은 출처에서 사용할 STOMP WebSocket 연결 엔드포인트를 등록한다.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // TODO STOMP CONNECT 단계에서 JWT를 검증하고 인증 사용자 Principal을 구성
        registry.addEndpoint("/ws");
    }
}
