package com.codeit.otboo.api.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.otboo.api.admin.dto.UserLockUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserRoleUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserSearchCondition;
import com.codeit.otboo.api.admin.service.AdminService;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.api.common.exception.GlobalExceptionHandler;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.exception.UserException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminControllerTest {

    private final AdminService adminService = mock(AdminService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminController(adminService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private UserDto userDto(String role, boolean locked) {
        return new UserDto(
                UUID.randomUUID(), Instant.now(), "woody@otboo.io", "우디", role, locked);
    }

    /** 사용자 목록 조회 시 커서 응답 형식으로 반환하는지 확인합니다. */
    @Test
    void returnsUserListInCursorFormat() throws Exception {
        CursorResponse<UserDto> response = CursorResponse.last(
                List.of(userDto("USER", false)), 1L, "createdAt", "DESCENDING");
        when(adminService.getUsers(any(UserSearchCondition.class))).thenReturn(response);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("woody@otboo.io"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    /** 검색 조건이 서비스로 전달되는지 확인합니다. */
    @Test
    void passesSearchConditionToService() throws Exception {
        when(adminService.getUsers(any(UserSearchCondition.class)))
                .thenReturn(CursorResponse.last(List.of(), 0L, "createdAt", "DESCENDING"));

        mockMvc.perform(get("/api/users")
                        .param("emailLike", "woody")
                        .param("roleEqual", "USER")
                        .param("locked", "false")
                        .param("limit", "10"))
                .andExpect(status().isOk());

        verify(adminService).getUsers(any(UserSearchCondition.class));
    }

    /** 권한 변경 성공 시 변경된 정보를 반환하는지 확인합니다. */
    @Test
    void returnsUpdatedRole() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.updateRole(eq(userId), any(UserRoleUpdateRequest.class)))
                .thenReturn(userDto("ADMIN", false));

        mockMvc.perform(patch("/api/users/{userId}/role", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRoleUpdateRequest(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    /** 권한이 비어 있으면 400 을 반환하는지 확인합니다. */
    @Test
    void returnsBadRequestOnMissingRole() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/users/{userId}/role", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.role").exists());

        verify(adminService, never()).updateRole(any(), any());
    }

    /** 계정 잠금 성공 시 잠금 상태를 반환하는지 확인합니다. */
    @Test
    void returnsUpdatedLockState() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.updateLock(eq(userId), any(UserLockUpdateRequest.class)))
                .thenReturn(userDto("USER", true));

        mockMvc.perform(patch("/api/users/{userId}/lock", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserLockUpdateRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));
    }

    /** 존재하지 않는 사용자면 404 를 반환하는지 확인합니다. */
    @Test
    void returnsNotFoundForUnknownUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.updateLock(eq(userId), any(UserLockUpdateRequest.class)))
                .thenThrow(UserException.notFound());

        mockMvc.perform(patch("/api/users/{userId}/lock", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserLockUpdateRequest(true))))
                .andExpect(status().isNotFound());
    }
}
