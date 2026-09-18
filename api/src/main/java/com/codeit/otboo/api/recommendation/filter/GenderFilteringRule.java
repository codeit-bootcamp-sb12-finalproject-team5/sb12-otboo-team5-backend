package com.codeit.otboo.api.recommendation.filter;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.profile.entity.Gender;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class GenderFilteringRule implements ClothesFilteringRule {

    @Override
    public boolean isSatisfied(Clothes clothes, ClothesFilteringContext context) {
        if (context.recommendationType() != RecommendationType.OUTFIT) {
            return true;
        }

        Gender profileGender = context.profileGender();
        if (profileGender == null || profileGender == Gender.OTHER) {
            return true;
        }

        ClothesGender clothesGender = clothes.getGender();
        return clothesGender == ClothesGender.BOTH
            || (profileGender == Gender.MALE && clothesGender == ClothesGender.MALE)
            || (profileGender == Gender.FEMALE && clothesGender == ClothesGender.FEMALE);
    }
}
