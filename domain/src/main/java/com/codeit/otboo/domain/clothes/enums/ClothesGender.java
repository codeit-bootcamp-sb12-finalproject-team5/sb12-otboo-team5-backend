package com.codeit.otboo.domain.clothes.enums;

import lombok.Getter;

@Getter
public enum ClothesGender implements Displayable {
    MALE("남성"),
    FEMALE("여성"),
    BOTH("혼성");

    private final String displayName;

    ClothesGender(String displayName) {
        this.displayName = displayName;
    }
}