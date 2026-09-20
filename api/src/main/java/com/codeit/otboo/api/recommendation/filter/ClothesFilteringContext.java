package com.codeit.otboo.api.recommendation.filter;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.profile.entity.Gender;
import java.math.BigDecimal;

/** Rule이 판단에 필요한 환경 값만 전달합니다. */
public record ClothesFilteringContext(
    BigDecimal effectiveTemperature,
    RecommendationType recommendationType,
    Gender profileGender
) {
}
