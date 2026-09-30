package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ClothesAttributeProvider {

    List<ClothesAttribute> attributes();
    String brand();
    String description();

    default String toEmbeddingText() {
        Stream<String> attributeStream = (attributes() == null || attributes().isEmpty())
            ? Stream.empty()
            : attributes().stream()
                .map(ClothesAttribute::toEmbeddingText)
                .filter(Objects::nonNull);
        Stream<String> descrptionStream = description() == null || description().isBlank()
                ? Stream.empty()
                : Stream.of("설명:" + description());
        return Stream.concat(attributeStream, descrptionStream)
                .collect(Collectors.joining("\n"));
    }

    default ClothesSubCategory getSubCategory() {
        if (attributes() == null || attributes().isEmpty()) {
            return null;
        }
        return attributes().stream()
            .filter(e -> e.definitionId().equals("2"))
            .map(e -> Displayable.from(ClothesSubCategory.class, e.value()))
            .findFirst()
            .orElseThrow(() ->
                new ClothesException(
                    ErrorCode.INVALID_ATTRIBUTE_VALUE
                ).addDetail("Get ClothesSubCategory Exception", null)
            );
    }

}
