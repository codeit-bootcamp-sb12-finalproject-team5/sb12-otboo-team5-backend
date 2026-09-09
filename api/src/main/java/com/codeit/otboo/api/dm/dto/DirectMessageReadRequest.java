package com.codeit.otboo.api.dm.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DirectMessageReadRequest(
        @NotNull UUID lastReadMessageId
) {
}
