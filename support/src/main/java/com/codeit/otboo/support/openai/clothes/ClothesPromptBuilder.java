package com.codeit.otboo.support.openai.clothes;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ClothesPromptBuilder {

    public String build(String url) {

        return """
            의류 상품을 분석하라.

            상품 URL:
            %s

            대분류와 소분류는 반드시 다음 관계를 따른다.

            %s

            분석 규칙:
            - 실제 상품 페이지를 확인한다.
            - 상품 페이지의 실제 상품명을 반환한다.
            - 상품 페이지의 실제 대표 상품 이미지 URL을 반환한다.
            - 상품의 대분류를 먼저 결정한다.
            - 결정한 대분류에 속한 소분류만 선택한다.
            - 소분류는 반드시 하나를 선택한다.
            - 모든 분류값은 JSON Schema의 허용값을 사용한다.
            - 확인할 수 없는 정보는 가장 합리적인 값을 선택한다.
            - 브랜드는 상품 페이지의 실제 브랜드명을 사용한다.
            """
            .formatted(url, buildCategoryRules());
    }

    private String buildCategoryRules() {

        return Arrays.stream(ClothesCategory.values())
            .map(category -> {
                String subCategories =
                    ClothesSubCategory.findByCategory(category)
                        .stream()
                        .map(ClothesSubCategory::getDisplayName)
                        .collect(Collectors.joining(", "));

                return "%s → [%s]"
                    .formatted(
                        category.getDisplayName(),
                        subCategories
                    );
            })
            .collect(Collectors.joining("\n"));
    }
}
