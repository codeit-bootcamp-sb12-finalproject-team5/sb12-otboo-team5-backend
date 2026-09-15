package com.codeit.otboo.api.recommendation.filter;

import com.codeit.otboo.domain.clothes.entity.Clothes;

/** Clothes가 현재 추천 환경에서 통과해야 하는 하나의 규칙입니다. */
public interface ClothesFilteringRule {

    boolean isSatisfied(Clothes clothes, ClothesFilteringContext context);
}
