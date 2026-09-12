package com.codeit.otboo.api.notification.dto.response;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import java.util.List;
import java.util.UUID;

public record NotificationReadResponse(List<NotificationDto> data, String nextCursor,
        UUID nextIdAfter, boolean hasNext, long totalCount, String sortBy,
        String sortDirection) {}
