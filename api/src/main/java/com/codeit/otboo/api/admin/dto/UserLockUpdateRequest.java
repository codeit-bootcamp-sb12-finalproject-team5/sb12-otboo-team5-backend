package com.codeit.otboo.api.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UserLockUpdateRequest(

        @NotNull(message = "잠금 여부는 필수입니다.")
        Boolean locked
) {
}
