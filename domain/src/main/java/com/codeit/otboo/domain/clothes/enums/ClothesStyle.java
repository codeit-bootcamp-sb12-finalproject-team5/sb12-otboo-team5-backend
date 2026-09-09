package com.codeit.otboo.domain.clothes.enums;

import lombok.Getter;

@Getter
public enum ClothesStyle implements Displayable {

    CASUAL("캐주얼"),
    STREET("스트릿"),
    GORPCORE("고프코어"),
    WORKWEAR("워크웨어"),
    PREPPY("프레피"),
    CITY_BOY("시티보이"),
    SPORTY("스포티"),
    ROMANTIC("로맨틱"),
    CLASSIC("클래식"),
    MINIMAL("미니멀"),
    CHIC("시크"),
    RETRO("레트로"),
    OTHER("기타");

    private final String displayName;

    ClothesStyle(String displayName) {
        this.displayName = displayName;
    }
}
