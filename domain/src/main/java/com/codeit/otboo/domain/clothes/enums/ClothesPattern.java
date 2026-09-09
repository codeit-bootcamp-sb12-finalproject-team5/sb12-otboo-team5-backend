package com.codeit.otboo.domain.clothes.enums;

import lombok.Getter;

@Getter
public enum ClothesPattern implements Displayable {

    LOGO_GRAPHIC("로고/그래픽"),
    SOLID("단색/무지"),
    STRIPED("스트라이프"),
    GARMENT_DYED("가먼트다잉"),
    COLOR_BLOCK("컬러블록"),
    CHECK("체크"),
    FLORAL("플라워"),
    DOT("도트"),
    CAMOUFLAGE("카모플라쥬"),
    GRADIENT("그라데이션"),
    DRAWING("드로잉"),
    DESTROYED("디스트로이드"),
    LETTERING("레터링"),
    OTHER("기타");

    private final String displayName;

    ClothesPattern(String displayName) {
        this.displayName = displayName;
    }
}
