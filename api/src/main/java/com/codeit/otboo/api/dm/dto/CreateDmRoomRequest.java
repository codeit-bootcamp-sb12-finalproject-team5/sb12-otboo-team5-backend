package com.codeit.otboo.api.dm.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDmRoomRequest(
    @NotNull(message = "receiverId는 필수입니다.")
    UUID receiverId
) {
}
