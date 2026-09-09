package com.codeit.otboo.domain.clothes.enums;

import lombok.Getter;

@Getter
public enum ClothesColor implements Displayable {

    BLACK("블랙"),
    WHITE("화이트"),
    DARK_GRAY("다크그레이"),
    GRAY("그레이"),
    NAVY("네이비"),
    IVORY("아이보리"),
    LIGHT_GRAY("라이트그레이"),
    KHAKI("카키"),
    BEIGE("베이지"),
    BLUE("블루"),
    DARK_NAVY("다크네이비"),
    BROWN("브라운"),
    DARK_BROWN("다크브라운"),
    BURGUNDY("버건디"),
    SKY_BLUE("스카이블루"),
    GREEN("그린"),
    DARK_GREEN("다크그린"),
    OLIVE_GREEN("올리브그린"),
    RED("레드"),
    DARK_BLUE("다크블루"),
    OATMEAL("오트밀"),
    MINT("민트"),
    PURPLE("퍼플"),
    DARK_BEIGE("다크베이지"),
    PINK("핑크"),
    ORANGE("오렌지"),
    LIGHT_PINK("라이트핑크"),
    LIGHT_GREEN("라이트그린"),
    YELLOW("옐로우"),
    DEEP_RED("딥레드"),
    LIGHT_BROWN("라이트브라운"),
    DARK_PINK("다크핑크"),
    SAND("샌드"),
    LIGHT_YELLOW("라이트옐로우"),
    MUSTARD("머스타드"),
    LAVENDER("라벤더"),
    LIME("라임"),
    DARK_ORANGE("다크오렌지"),
    CAMEL("카멜"),
    BRICK("브릭"),
    SILVER("실버"),
    LIGHT_ORANGE("라이트오렌지"),
    PALE_PINK("페일핑크"),
    PEACH("피치"),
    DENIM("데님"),
    KHAKI_BEIGE("카키베이지"),
    BLACK_DENIM("흑청"),
    LIGHT_DENIM("연청"),
    MID_DENIM("중청"),
    GOLD("골드"),
    CLEAR("클리어"),
    OTHER("기타");

    private final String displayName;

    ClothesColor(String displayName) {
        this.displayName = displayName;
    }
}