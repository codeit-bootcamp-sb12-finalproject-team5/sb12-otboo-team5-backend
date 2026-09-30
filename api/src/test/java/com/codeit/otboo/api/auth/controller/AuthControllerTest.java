package com.codeit.otboo.api.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.otboo.api.auth.dto.JwtDto;
import com.codeit.otboo.api.auth.dto.ResetPasswordRequest;
import com.codeit.otboo.api.auth.dto.SignInRequest;
import com.codeit.otboo.api.auth.service.AuthService;
import com.codeit.otboo.api.common.exception.GlobalExceptionHandler;
import com.codeit.otboo.domain.user.exception.UserException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    /** 로그인 성공 시 토큰을 반환하고 Refresh Token 쿠키를 내려주는지 확인합니다. */
    @Test
    void returnsTokensAndSetsRefreshCookieOnSignIn() throws Exception {
        when(authService.signIn(any(SignInRequest.class)))
                .thenReturn(new JwtDto(null, "access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignInRequest("woody@otboo.io", "otboo1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(cookie().value("refresh_token", "refresh-token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    /** 인증 실패 시 401 을 반환하는지 확인합니다. */
    @Test
    void returnsUnauthorizedOnInvalidCredentials() throws Exception {
        when(authService.signIn(any(SignInRequest.class)))
                .thenThrow(UserException.invalidCredentials());

        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignInRequest("woody@otboo.io", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    /** 잠긴 계정 로그인 시 403 을 반환하는지 확인합니다. */
    @Test
    void returnsForbiddenOnLockedAccount() throws Exception {
        when(authService.signIn(any(SignInRequest.class)))
                .thenThrow(UserException.accountLocked());

        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignInRequest("woody@otboo.io", "otboo1234"))))
                .andExpect(status().isForbidden());
    }

    /** 이메일이 비어 있으면 400 을 반환하는지 확인합니다. */
    @Test
    void returnsBadRequestOnBlankEmail() throws Exception {
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignInRequest("", "otboo1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());

        verify(authService, never()).signIn(any(SignInRequest.class));
    }

    /** 비밀번호 초기화 요청 시 204 를 반환하는지 확인합니다. */
    @Test
    void returnsNoContentOnPasswordReset() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("woody@otboo.io"))))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword("woody@otboo.io");
    }

    /** 이메일 형식이 잘못되면 초기화 요청을 거부하는지 확인합니다. */
    @Test
    void rejectsPasswordResetWithInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("not-an-email"))))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resetPassword(anyString());
    }

    /** Refresh Token 쿠키가 없으면 재발급을 거부하는지 확인합니다. */
    @Test
    void rejectsRefreshWithoutCookie() throws Exception {
        when(authService.refresh(null))
                .thenThrow(new UserException(
                        com.codeit.otboo.domain.common.exception.ErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    /** 토큰 재발급 성공 시 새 쿠키를 내려주는지 확인합니다. */
    @Test
    void setsNewCookieOnRefresh() throws Exception {
        when(authService.refresh("old-refresh"))
                .thenReturn(new JwtDto(null, "new-access", "new-refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(cookie().value("refresh_token", "new-refresh"));
    }
}
