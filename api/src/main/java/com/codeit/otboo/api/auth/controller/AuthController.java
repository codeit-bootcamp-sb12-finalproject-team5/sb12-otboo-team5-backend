package com.codeit.otboo.api.auth.controller;

import com.codeit.otboo.api.auth.dto.JwtDto;
import com.codeit.otboo.api.auth.dto.ResetPasswordRequest;
import com.codeit.otboo.api.auth.dto.SignInRequest;
import com.codeit.otboo.api.auth.service.AuthService;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;

    /** 로그인 */
    @PostMapping("/sign-in")
    public ResponseEntity<JwtDto> signIn(@Valid @RequestBody SignInRequest request,
                                        HttpServletResponse response) {
        JwtDto tokens = authService.signIn(request);
        addRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(tokens);
    }

    /** 로그아웃 */
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(@AuthenticationPrincipal CustomUserDetails principal,
                                       HttpServletResponse response) {
        authService.signOut(principal.getUserId());
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    /** 토큰 재발급 */
    @PostMapping("/refresh")
    public ResponseEntity<JwtDto> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {

        JwtDto tokens = authService.refresh(refreshToken);
        addRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(tokens);
    }

    /** 비밀번호 초기화 - 임시 비밀번호 발급 */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email());
        return ResponseEntity.noContent().build();
    }

    /** CSRF 토큰 조회 - 응답 시 XSRF-TOKEN 쿠키가 발급됩니다 */
    @GetMapping("/csrf-token")
    public ResponseEntity<Void> csrfToken(CsrfToken csrfToken) {
        // getToken() 을 호출해야 실제로 토큰이 생성되고 쿠키가 내려갑니다
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    private void addRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
