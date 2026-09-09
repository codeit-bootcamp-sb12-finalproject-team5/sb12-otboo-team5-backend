package com.codeit.otboo.api.admin.dto;

import com.codeit.otboo.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(

        @NotNull(message = "권한은 필수입니다.")
        UserRole role
) {
}
