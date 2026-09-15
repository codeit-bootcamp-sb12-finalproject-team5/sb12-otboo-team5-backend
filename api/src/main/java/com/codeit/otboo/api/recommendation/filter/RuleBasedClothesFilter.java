package com.codeit.otboo.api.recommendation.filter;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuleBasedClothesFilter {

    private final List<ClothesFilteringRule> filteringRules;

    /** 등록된 모든 Rule을 통과한 Clothes만 다음 Ranking 단계로 전달합니다. */
    public List<Clothes> filter(List<Clothes> candidates, ClothesFilteringContext context) {
        return candidates.stream()
            .filter(clothes -> filteringRules.stream()
                .allMatch(rule -> rule.isSatisfied(clothes, context)))
            .toList();
    }
}
