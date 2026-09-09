package com.codeit.otboo.api.clothes.dto;

import java.util.UUID;

public record ClothingAttribute(
    UUID definitionId,
    String definitionName,
    String[] selectableValues,
    String value
) {
}
