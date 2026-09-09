package com.codeit.otboo.domain.clothes.enums;

import lombok.Getter;

@Getter
public enum ClothesFit implements Displayable {

    STANDARD("스탠다드"),
    OVERSIZED("오버사이즈"),
    SLIM("슬림"),
    WIDE("와이드");

    private final String displayName;

    ClothesFit(String displayName) {
        this.displayName = displayName;
    }
}
