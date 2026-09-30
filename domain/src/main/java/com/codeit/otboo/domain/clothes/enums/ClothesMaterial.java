package com.codeit.otboo.domain.clothes.enums;

import lombok.Getter;

@Getter
public enum ClothesMaterial implements Displayable {

    COTTON("면"),
    POLYESTER("폴리에스테르"),
    SPANDEX("스판덱스"),
    NYLON("나일론"),
    KNIT("니트"),
    RAYON("레이온"),
    ACRYLIC("아크릴"),
    FLEECE_LINED("기모"),
    WOOL("울"),
    TENCEL("텐셀"),
    POLYURETHANE("폴리우레탄"),
    MESH("메시"),
    DENIM("데님"),
    CASHMERE("캐시미어"),
    COTTON_BLEND("면혼방"),
    FLEECE("플리스"),
    ALPACA("알파카"),
    MOHAIR("모헤어"),
    SILK("실크"),
    CORDUROY("코듀로이"),
    VELVET("벨벳"),
    OTHER("기타");

    private final String displayName;

    ClothesMaterial(String displayName) {
        this.displayName = displayName;
    }
}
