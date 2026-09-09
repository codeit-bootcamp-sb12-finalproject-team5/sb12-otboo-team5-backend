package com.codeit.otboo.api.clothes.dto;

import java.util.List;
import java.util.UUID;

public record ClothesAttribute(
    UUID definitionId,
    String definitionName,
    List<String> selectableValues,
    String value
) {
}
