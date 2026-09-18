package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 이후 LLM Outfit Generation에 전달할 카테고리별 후보 경계 객체입니다. */
public record RankedClothesCandidates(Map<ClothesCategory, List<RankedClothes>> byCategory) {
    public RankedClothesCandidates {
        Map<ClothesCategory, List<RankedClothes>> input = byCategory;
        byCategory = Arrays.stream(ClothesCategory.values()).collect(java.util.stream.Collectors.toUnmodifiableMap(
            category -> category,
            category -> List.copyOf(input.getOrDefault(category, List.of()))
        ));
    }

    public static RankedClothesCandidates empty() {
        return new RankedClothesCandidates(Map.of());
    }

    public List<RankedClothes> category(ClothesCategory category) {
        return byCategory.get(category);
    }

    public Stream<RankedClothes> all() {
        return byCategory.values().stream().flatMap(List::stream);
    }

    public boolean isRecommendable() {
        boolean hasTwoPiece = !category(ClothesCategory.TOP).isEmpty()
            && (!category(ClothesCategory.PANTS).isEmpty() || !category(ClothesCategory.SKIRT).isEmpty());
        boolean hasOnePiece = !category(ClothesCategory.DRESS).isEmpty();
        return hasTwoPiece || hasOnePiece;
    }
}
