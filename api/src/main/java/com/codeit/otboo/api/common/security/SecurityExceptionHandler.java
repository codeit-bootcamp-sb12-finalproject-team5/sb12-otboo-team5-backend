package com.codeit.otboo.api.common.security;

import com.codeit.otboo.api.common.dto.ErrorResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Security 필터에서 발생하는 예외는 컨트롤러에 도달하지 않아
 * GlobalExceptionHandler 가 처리하지 못합니다.
 * 동일한 ErrorResponse 형식으로 응답하기 위해 별도로 구현합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 인증 실패 (401) */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[UNAUTHORIZED] {} {}", request.getMethod(), request.getRequestURI());
        write(response, ErrorCode.INVALID_TOKEN, "AuthenticationException");
    }

    /** 인가 실패 (403) */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = (auth != null && auth.getName() != null) ? auth.getName() : "Anonymous";
        log.warn("[FORBIDDEN] {} {} - User: [{}] | Reason: [{}] {}",
            request.getMethod(),
            request.getRequestURI(),
            currentUser,
            accessDeniedException.getClass().getSimpleName(),
            accessDeniedException.getMessage()
        );
        write(response, ErrorCode.ACCESS_DENIED, "AccessDeniedException");
    }

    private void write(HttpServletResponse response, ErrorCode code, String exceptionName)
            throws IOException {
        response.setStatus(code.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = ErrorResponse.of(exceptionName, code.getMessage());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
