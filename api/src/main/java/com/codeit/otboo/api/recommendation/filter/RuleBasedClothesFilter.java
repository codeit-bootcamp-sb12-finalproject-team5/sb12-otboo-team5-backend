package com.codeit.otboo.api.recommendation.filter;

import com.codeit.otboo.domain.clothes.entity.Clothes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedClothesFilter {

    private final List<ClothesFilteringRule> filteringRules;

    /**
     * 등록된 모든 Rule을 통과한 Clothes만 다음 Ranking 단계로 전달합니다.
     */
    public List<Clothes> filter(List<Clothes> candidates, ClothesFilteringContext context) {
        Map<String, Integer> failureCountByRule = new LinkedHashMap<>();
        filteringRules.forEach(rule -> failureCountByRule.put(rule.getClass().getSimpleName(), 0));

        List<Clothes> filtered = candidates.stream()
            .filter(clothes -> {
                boolean passed = true;

                for (ClothesFilteringRule rule : filteringRules) {
                    if (!rule.isSatisfied(clothes, context)) {
                        passed = false;
                        failureCountByRule.computeIfPresent(
                            rule.getClass().getSimpleName(),
                            (name, count) -> count + 1
                        );
                    }
                }

                return passed;
            })
            .toList();

        log.info(
            "[recommendation][filter] 규칙 검사 완료 type={}, inputCount={}, outputCount={}, "
                + "rejectedCount={}, failureCountByRule={}",
            context.recommendationType(),
            candidates.size(),
            filtered.size(),
            candidates.size() - filtered.size(),
            failureCountByRule
        );

        return filtered;
    }
}
