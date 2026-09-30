package com.codeit.otboo.api.common.security;

import com.codeit.otboo.api.auth.dto.JwtDto;
import com.codeit.otboo.api.auth.service.AuthService;
import com.codeit.otboo.api.auth.service.CustomOidcUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

@Component
@RequiredArgsConstructor
public class OAuthLoginSuccessHandler
        implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuthProperties oauthProperties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        CustomOidcUser principal =
                (CustomOidcUser) authentication.getPrincipal();

        // 우리 DB User PK
        UUID userId = principal.getUserId();

        // 기존 AuthService의 JWT 발급 정책 그대로 사용
        JwtDto jwtDto = authService.signInOauth(userId);

        // OAuth 로그인 과정에서 사용한 세션 제거
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        addRefreshCookie(response, jwtDto.refreshToken());

        response.sendRedirect(oauthProperties.frontendRedirectUri());
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
