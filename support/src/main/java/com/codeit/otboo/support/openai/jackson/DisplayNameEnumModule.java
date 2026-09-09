package com.codeit.otboo.support.openai.jackson;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class DisplayNameEnumModule extends SimpleModule {

    public DisplayNameEnumModule() {

        addDeserializer(
            ClothesGender.class,
            new DisplayNameEnumDeserializer<>(ClothesGender.class)
        );

        addDeserializer(
            ClothesCategory.class,
            new DisplayNameEnumDeserializer<>(ClothesCategory.class)
        );

        addDeserializer(
            ClothesSubCategory.class,
            new DisplayNameEnumDeserializer<>(ClothesSubCategory.class)
        );

        addDeserializer(
            ClothesColor.class,
            new DisplayNameEnumDeserializer<>(ClothesColor.class)
        );

        addDeserializer(
            ClothesFit.class,
            new DisplayNameEnumDeserializer<>(ClothesFit.class)
        );

        addDeserializer(
            ClothesMaterial.class,
            new DisplayNameEnumDeserializer<>(ClothesMaterial.class)
        );

        addDeserializer(
            ClothesPattern.class,
            new DisplayNameEnumDeserializer<>(ClothesPattern.class)
        );

        addDeserializer(
            ClothesStyle.class,
            new DisplayNameEnumDeserializer<>(ClothesStyle.class)
        );

        addDeserializer(
            ClothesSeason.class,
            new DisplayNameEnumDeserializer<>(ClothesSeason.class)
        );
    }
}
