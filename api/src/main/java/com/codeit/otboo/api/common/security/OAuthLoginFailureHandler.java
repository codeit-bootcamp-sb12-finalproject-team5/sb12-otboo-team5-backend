package com.codeit.otboo.api.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthLoginFailureHandler
        implements AuthenticationFailureHandler {

    private final OAuthProperties oauthProperties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        log.warn("OAuth 로그인 실패", exception);

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        String separator = oauthProperties.frontendRedirectUri().contains("?") ? "&" : "?";
        String redirectUri = oauthProperties.frontendRedirectUri()
                + separator + "error=oauth_login_failed";
        response.sendRedirect(redirectUri);
    }
}
