package com.codeit.otboo.domain.clothes.enums;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum ClothesSubCategory implements Displayable {

    // =========================
    // TOP - 상의
    // =========================

    LONG_SLEEVE_TEE(
        "긴소매티",
        ClothesCategory.TOP
    ),

    SWEATSHIRT(
        "스웨트셔츠",
        ClothesCategory.TOP
    ),

    SHIRT_BLOUSE(
        "셔츠/블라우스",
        ClothesCategory.TOP
    ),

    HOODIE(
        "후드티",
        ClothesCategory.TOP
    ),

    SHORT_SLEEVE_TEE(
        "반소매티",
        ClothesCategory.TOP
    ),

    POLO_SHIRT(
        "카라티",
        ClothesCategory.TOP
    ),

    KNITWEAR(
        "니트",
        ClothesCategory.TOP
    ),

    SLEEVELESS(
        "민소매티",
        ClothesCategory.TOP
    ),

    OTHER_TOP(
        "기타",
        ClothesCategory.TOP
    ),

    // =========================
    // OUTER - 아우터
    // =========================

    ZIP_UP_HOODIE(
        "후드집업",
        ClothesCategory.OUTER
    ),

    BLOUSON(
        "블루종",
        ClothesCategory.OUTER
    ),

    LEATHER_JACKET(
        "레더",
        ClothesCategory.OUTER
    ),

    SUIT_JACKET(
        "슈트재킷",
        ClothesCategory.OUTER
    ),

    CARDIGAN(
        "카디건",
        ClothesCategory.OUTER
    ),

    LIGHTWEIGHT_PUFFER(
        "경량패딩",
        ClothesCategory.OUTER
    ),

    HUNTING_JACKET(
        "헌팅재킷",
        ClothesCategory.OUTER
    ),

    TRUCKER_JACKET(
        "트러커재킷",
        ClothesCategory.OUTER
    ),

    VARSITY_JACKET(
        "스타디움재킷",
        ClothesCategory.OUTER
    ),

    NYLON_JACKET(
        "나일론재킷",
        ClothesCategory.OUTER
    ),

    TRACK_JACKET(
        "트레이닝재킷",
        ClothesCategory.OUTER
    ),

    ANORAK(
        "아노락재킷",
        ClothesCategory.OUTER
    ),

    FLEECE_JACKET(
        "플리스",
        ClothesCategory.OUTER
    ),

    MID_SEASON_COAT(
        "환절기코트",
        ClothesCategory.OUTER
    ),

    VEST(
        "베스트",
        ClothesCategory.OUTER
    ),

    SHEARLING_JACKET(
        "무스탕",
        ClothesCategory.OUTER
    ),

    SINGLE_COAT(
        "싱글코트",
        ClothesCategory.OUTER
    ),

    DOUBLE_COAT(
        "더블코트",
        ClothesCategory.OUTER
    ),

    OTHER_COAT(
        "기타코트",
        ClothesCategory.OUTER
    ),

    LONG_PUFFER(
        "롱패딩",
        ClothesCategory.OUTER
    ),

    SHORT_PUFFER(
        "숏패딩",
        ClothesCategory.OUTER
    ),

    OTHER_OUTER(
        "기타",
        ClothesCategory.OUTER
    ),

    // =========================
    // BOTTOM - PANTS
    // =========================

    DENIM_PANTS(
        "데님팬츠",
        ClothesCategory.PANTS
    ),

    JOGGER_PANTS(
        "조거팬츠",
        ClothesCategory.PANTS
    ),

    COTTON_PANTS(
        "코튼팬츠",
        ClothesCategory.PANTS
    ),

    SLACKS(
        "슈트팬츠",
        ClothesCategory.PANTS
    ),

    SHORTS(
        "숏팬츠",
        ClothesCategory.PANTS
    ),

    LEGGINGS(
        "레깅스",
        ClothesCategory.PANTS
    ),

    JUMPSUIT(
        "점프슈트",
        ClothesCategory.PANTS
    ),

    // =========================
    // BOTTOM - SKIRT
    // =========================

    MINI_SKIRT(
        "미니스커트",
        ClothesCategory.SKIRT
    ),

    MIDI_SKIRT(
        "미디스커트",
        ClothesCategory.SKIRT
    ),

    LONG_SKIRT(
        "롱스커트",
        ClothesCategory.SKIRT
    ),

    OTHER_BOTTOM(
        "기타",
        ClothesCategory.SKIRT
    ),

    // =========================
    // DRESS
    // =========================

    MINI_DRESS(
        "미니원피스",
        ClothesCategory.DRESS
    ),

    MIDI_DRESS(
        "미디원피스",
        ClothesCategory.DRESS
    ),

    MAXI_DRESS(
        "맥시원피스",
        ClothesCategory.DRESS
    ),

    // =========================
    // BAG
    // =========================

    MESSENGER_BAG(
        "메신저백",
        ClothesCategory.BAG
    ),

    SHOULDER_BAG(
        "숄더백",
        ClothesCategory.BAG
    ),

    BACKPACK(
        "백팩",
        ClothesCategory.BAG
    ),

    TOTE_BAG(
        "토트백",
        ClothesCategory.BAG
    ),

    ECO_BAG(
        "에코백",
        ClothesCategory.BAG
    ),

    BOSTON_BAG(
        "보스턴백",
        ClothesCategory.BAG
    ),

    WAIST_BAG(
        "웨이스트백",
        ClothesCategory.BAG
    ),

    POUCH(
        "파우치",
        ClothesCategory.BAG
    ),

    BRIEFCASE(
        "브리프케이스",
        ClothesCategory.BAG
    ),

    LUGGAGE(
        "캐리어",
        ClothesCategory.BAG
    ),

    CLUTCH_BAG(
        "클러치백",
        ClothesCategory.BAG
    ),

    // =========================
    // HAT
    // =========================

    CAP(
        "캡",
        ClothesCategory.HAT
    ),

    BERET(
        "베레모",
        ClothesCategory.HAT
    ),

    FEDORA(
        "페도라",
        ClothesCategory.HAT
    ),

    BUCKET_HAT(
        "버킷",
        ClothesCategory.HAT
    ),

    BEANIE(
        "비니",
        ClothesCategory.HAT
    ),

    TRAPPER_HAT(
        "트루퍼",
        ClothesCategory.HAT
    ),

    BALACLAVA(
        "바라클라바",
        ClothesCategory.HAT
    ),

    OTHER_HAT(
        "기타",
        ClothesCategory.HAT
    ),

    // =========================
    // SHOES
    // =========================

    SNEAKERS(
        "스니커즈",
        ClothesCategory.SHOES
    ),

    SPORTS_SHOES(
        "스포츠",
        ClothesCategory.SHOES
    ),

    DRESS_SHOES(
        "구두",
        ClothesCategory.SHOES
    ),

    BOOTS(
        "부츠",
        ClothesCategory.SHOES
    ),

    SANDALS(
        "샌들",
        ClothesCategory.SHOES
    ),

    PADDED_SHOES(
        "패딩",
        ClothesCategory.SHOES
    ),

    OTHER_SHOES(
        "기타",
        ClothesCategory.SHOES
    ),

    // =========================
    // ACCESSORY
    // =========================

    MUFFLER(
        "머플러",
        ClothesCategory.ACCESSORY
    ),

    JEWELRY(
        "주얼리",
        ClothesCategory.ACCESSORY
    ),

    GLASSES(
        "안경",
        ClothesCategory.ACCESSORY
    ),

    WATCH(
        "시계",
        ClothesCategory.ACCESSORY
    ),

    BELT(
        "벨트",
        ClothesCategory.ACCESSORY
    ),

    OTHER_ACCESSORY(
        "기타",
        ClothesCategory.ACCESSORY
    );

    private final String displayName;
    private final ClothesCategory category;

    ClothesSubCategory(
        String displayName,
        ClothesCategory category
    ) {
        this.displayName = displayName;
        this.category = category;
    }

    public static List<ClothesSubCategory> findByCategory(
        ClothesCategory category
    ) {
        return Arrays.stream(values())
            .filter(subCategory ->
                subCategory.category == category
            )
            .toList();
    }
}