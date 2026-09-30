package com.codeit.otboo.api.user.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.user.dto.ChangePasswordRequest;
import com.codeit.otboo.api.user.dto.UserCreateRequest;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 회원가입 */
    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest request) {
        UserDto created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 비밀번호 변경 (본인만) */
    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        if (!principal.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("본인만 변경할 수 있습니다.");
        }

        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
