package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesGender;
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

    default String toEmbeddingText() {
        Stream<String> attributeStream = (attributes() == null || attributes().isEmpty())
            ? Stream.empty()
            : attributes().stream()
                .map(ClothesAttribute::toEmbeddingText)
                .filter(Objects::nonNull);
        Stream<String> brandStream = (brand() != null && !brand().isBlank())
            ? Stream.of("브랜드:" + brand())
            : Stream.empty();
        return Stream.concat(attributeStream, brandStream)
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

    default public ClothesGender getGender() {
        if (attributes() == null || attributes().isEmpty()) {
            return null;
        }
        return attributes().stream()
            .filter(e -> e.definitionId().equals("9"))
            .map(e -> Displayable.from(ClothesGender.class, e.value()))
            .findFirst()
            .orElseThrow(() ->
                new ClothesException(
                    ErrorCode.INVALID_ATTRIBUTE_VALUE
                ).addDetail("Get ClothesGender Exception", null)
            );
    }

}
