package com.codeit.otboo.api.recommendation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserPreferenceRequestTest {

    @Test
    void createsEmbeddingTextInAttributeOrder() {
        UserPreferenceRequest request = new UserPreferenceRequest(
                List.of(ClothesSubCategory.LONG_SLEEVE_TEE, ClothesSubCategory.SHIRT_BLOUSE,
                        ClothesSubCategory.DENIM_PANTS),
                List.of(ClothesColor.BLACK, ClothesColor.WHITE),
                List.of(ClothesFit.STANDARD, ClothesFit.OVERSIZED),
                List.of(ClothesMaterial.COTTON, ClothesMaterial.DENIM),
                List.of(ClothesPattern.SOLID, ClothesPattern.STRIPED),
                List.of(ClothesStyle.CASUAL, ClothesStyle.MINIMAL));

        assertThat(request.toEmbeddingText()).isEqualTo("""
                카테고리:긴소매티, 셔츠/블라우스, 데님팬츠
                색상:블랙, 화이트
                핏:스탠다드, 오버사이즈
                소재:면, 데님
                패턴:단색/무지, 스트라이프
                스타일:캐주얼, 미니멀""");
    }
}
