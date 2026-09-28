package com.codeit.otboo.support.llm.outfit.dto;

public record GeneratedOutfitImage(
        byte[] imageBytes,
        String mimeType,
        String model,
        String prompt
) {
}
