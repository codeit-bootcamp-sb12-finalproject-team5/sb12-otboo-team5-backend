package com.codeit.otboo.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeit.otboo.api.auth.dto.JwtDto;
import com.codeit.otboo.api.auth.service.AuthService;
import com.codeit.otboo.api.auth.service.CustomOidcUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

class OAuthLoginSuccessHandlerTest {

    private final AuthService authService = mock(AuthService.class);
    private final OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(
            authService, new OAuthProperties("http://localhost:5173/#/auth/oauth/callback"));

    @Test
    void redirectsToFrontendAndSetsRefreshCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        OidcIdToken idToken = new OidcIdToken(
                "id-token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "google-id"));
        CustomOidcUser principal = new CustomOidcUser(
                userId,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken,
                new OidcUserInfo(Map.of("sub", "google-id")));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authService.signInOauth(userId))
                .thenReturn(new JwtDto(null, "access-token", "refresh-token"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/#/auth/oauth/callback");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("refresh_token=refresh-token", "HttpOnly", "SameSite=Lax");
    }
}
