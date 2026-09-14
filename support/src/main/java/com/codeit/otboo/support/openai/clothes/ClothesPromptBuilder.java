package com.codeit.otboo.support.openai.clothes;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

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
            
            - 상품의 특징을 바탕으로 간결한 자연어 설명을 작성한다.
            - 설명에는 색상, 소재, 핏, 패턴, 스타일 등 확인된 주요 특징을 자연스럽게 포함한다.
            - 확인할 수 없는 속성을 임의로 만들어 설명에 포함하지 않는다.
            - 설명은 1~2개의 문장으로 줄바꿈 없이 한줄로만 작성한다.
            - 상품명이나 브랜드명을 불필요하게 반복하지 않는다.
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

                    return """
                    - %s
                      가능한 소분류: [%s]
                    """.formatted(
                            category.getDisplayName(),
                            subCategories
                    );
                })
                .collect(Collectors.joining("\n"));
    }
}
