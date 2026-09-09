package com.codeit.otboo.api.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.otboo.api.common.exception.GlobalExceptionHandler;
import com.codeit.otboo.api.user.dto.UserCreateRequest;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.api.user.service.UserService;
import com.codeit.otboo.domain.user.exception.UserException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private final UserService userService = org.mockito.Mockito.mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserController(userService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    /** 회원가입 성공 시 201 과 사용자 정보를 반환하는지 확인합니다. */
    @Test
    void returnsCreatedOnSignUp() throws Exception {
        UserDto response = new UserDto(
                UUID.randomUUID(), Instant.now(), "woody@otboo.io", "우디", "USER", false);
        when(userService.create(any(UserCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserCreateRequest("우디", "woody@otboo.io", "otboo1234"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("woody@otboo.io"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.locked").value(false));
    }

    /** 이메일 형식이 잘못되면 400 과 필드별 오류를 반환하는지 확인합니다. */
    @Test
    void returnsBadRequestOnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserCreateRequest("우디", "not-an-email", "otboo1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());

        verify(userService, never()).create(any(UserCreateRequest.class));
    }

    /** 필수값이 비어 있으면 400 과 필드별 오류를 반환하는지 확인합니다. */
    @Test
    void returnsBadRequestOnBlankFields() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserCreateRequest("", "woody@otboo.io", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    /** 이메일이 중복되면 409 를 반환하는지 확인합니다. */
    @Test
    void returnsConflictOnDuplicateEmail() throws Exception {
        when(userService.create(any(UserCreateRequest.class)))
                .thenThrow(UserException.duplicateEmail());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserCreateRequest("우디", "woody@otboo.io", "otboo1234"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.exceptionName").value("UserException"));
    }
}
