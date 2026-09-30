package com.codeit.otboo.api.notification.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record NotificationReadRequest(
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit
) {
}
