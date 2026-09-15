package com.codeit.otboo.domain.clothes.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ClothesSeason implements Displayable {

    SPRING("봄"),
    SUMMER("여름"),
    FALL("가을"),
    WINTER("겨울"),
    ALL_SEASON("사계절"),
    MID_SEASON("간절기");

    private final String displayName;

    ClothesSeason(String displayName) {
        this.displayName = displayName;
    }

    @Override
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}
