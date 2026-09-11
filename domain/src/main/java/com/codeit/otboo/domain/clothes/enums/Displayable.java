package com.codeit.otboo.domain.clothes.enums;

import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import java.util.Arrays;

public interface Displayable {
    String getDisplayName();

    static <E extends Enum<E> & Displayable> E from(
        Class<E> enumClass,
        String value
    ) {
        return Arrays.stream(enumClass.getEnumConstants())
            .filter(e -> e.getDisplayName().equals(value))
            .findFirst()
            .orElseThrow(() ->
                new ClothesException(
                    ErrorCode.INVALID_ATTRIBUTE_VALUE
                )
            );
    }
}
