package com.codeit.otboo.support.openai.jackson;

import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

public class DisplayNameEnumDeserializer<T extends Enum<T> & Displayable>
    extends JsonDeserializer<T> {

    private final Class<T> enumType;

    public DisplayNameEnumDeserializer(Class<T> enumType) {
        this.enumType = enumType;
    }

    @Override
    public T deserialize(
        JsonParser parser,
        DeserializationContext context
    ) throws IOException {

        String value = parser.getText();

        for (T constant : enumType.getEnumConstants()) {
            if (constant.getDisplayName().equals(value)) {
                return constant;
            }
        }

        throw InvalidFormatException.from(
            parser,
            "지원하지 않는 enum 값입니다.",
            value,
            enumType
        );
    }
}
