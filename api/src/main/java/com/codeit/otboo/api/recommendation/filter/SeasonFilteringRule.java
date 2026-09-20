package com.codeit.otboo.api.recommendation.filter;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class SeasonFilteringRule implements ClothesFilteringRule {

    @Override
    public boolean isSatisfied(Clothes clothes, ClothesFilteringContext context) {
        ClothesSeason season = clothes.getSeason();
        if (season == null) {
            return false;
        }
        return allowedSeasons(context.effectiveTemperature()).contains(season);
    }

    private Set<ClothesSeason> allowedSeasons(BigDecimal effectiveTemperature) {
        if (effectiveTemperature.compareTo(BigDecimal.valueOf(4)) <= 0) {
            return EnumSet.of(ClothesSeason.WINTER, ClothesSeason.ALL_SEASON);
        }
        if (effectiveTemperature.compareTo(BigDecimal.valueOf(12)) < 0) {
            return EnumSet.of(ClothesSeason.FALL, ClothesSeason.WINTER, ClothesSeason.ALL_SEASON);
        }
        if (effectiveTemperature.compareTo(BigDecimal.valueOf(17)) < 0) {
            return EnumSet.of(ClothesSeason.FALL, ClothesSeason.SPRING, ClothesSeason.MID_SEASON,
                ClothesSeason.ALL_SEASON);
        }
        if (effectiveTemperature.compareTo(BigDecimal.valueOf(23)) < 0) {
            return EnumSet.of(ClothesSeason.FALL, ClothesSeason.SPRING, ClothesSeason.ALL_SEASON);
        }
        if (effectiveTemperature.compareTo(BigDecimal.valueOf(28)) < 0) {
            return EnumSet.of(ClothesSeason.SPRING, ClothesSeason.SUMMER, ClothesSeason.ALL_SEASON);
        }
        return EnumSet.of(ClothesSeason.SUMMER, ClothesSeason.ALL_SEASON);
    }
}
