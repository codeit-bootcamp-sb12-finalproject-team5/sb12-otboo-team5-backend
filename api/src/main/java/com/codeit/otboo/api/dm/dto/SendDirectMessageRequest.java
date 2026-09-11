package com.codeit.otboo.api.dm.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SendDirectMessageRequest(
        @NotNull(message = "roomId는 필수입니다.")
        UUID roomId,
        @NotNull(message = "receiverId는 필수입니다.")
        UUID receiverId,
        @NotNull(message = "content는 필수입니다.")
        String content
) {
}
