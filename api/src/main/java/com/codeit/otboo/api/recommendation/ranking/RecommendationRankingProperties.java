package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommendation.ranking.top-k")
public record RecommendationRankingProperties(
    int top,
    int pants,
    int skirt,
    int outer,
    int dress,
    int shoes,
    int hat,
    int bag,
    int accessory
) {
    public Map<ClothesCategory, Integer> limits() {
        return Map.of(
            ClothesCategory.TOP, top,
            ClothesCategory.PANTS, pants,
            ClothesCategory.SKIRT, skirt,
            ClothesCategory.OUTER, outer,
            ClothesCategory.DRESS, dress,
            ClothesCategory.SHOES, shoes,
            ClothesCategory.HAT, hat,
            ClothesCategory.BAG, bag,
            ClothesCategory.ACCESSORY, accessory
        );
    }
}
