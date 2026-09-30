package com.codeit.otboo.api.admin.controller;

import com.codeit.otboo.api.admin.dto.UserLockUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserRoleUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserSearchCondition;
import com.codeit.otboo.api.admin.service.AdminService;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.domain.user.entity.UserRole;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 API.
 * 접근 권한은 SecurityConfig 에서 ADMIN 으로 제한합니다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 사용자 목록 조회 */
    @GetMapping
    public ResponseEntity<CursorResponse<UserDto>> getUsers(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String emailLike,
            @RequestParam(required = false) UserRole roleEqual,
            @RequestParam(required = false) Boolean locked) {

        UserSearchCondition condition = new UserSearchCondition(
                cursor, idAfter, limit, sortBy, sortDirection, emailLike, roleEqual, locked);

        return ResponseEntity.ok(adminService.getUsers(condition));
    }

    /** 권한 변경 */
    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserDto> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UserRoleUpdateRequest request) {

        return ResponseEntity.ok(adminService.updateRole(userId, request));
    }

    /** 계정 잠금 상태 변경 */
    @PatchMapping("/{userId}/lock")
    public ResponseEntity<UserDto> updateLock(
            @PathVariable UUID userId,
            @Valid @RequestBody UserLockUpdateRequest request) {

        return ResponseEntity.ok(adminService.updateLock(userId, request));
    }
}
