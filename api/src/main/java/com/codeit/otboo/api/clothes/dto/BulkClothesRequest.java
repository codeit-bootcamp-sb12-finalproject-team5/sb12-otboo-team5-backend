package com.codeit.otboo.api.clothes.dto;

import java.util.List;
import java.util.UUID;

public record BulkClothesRequest(
        UUID userId,
        List<String> links
) {
}
