package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.entity.Clothes;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 이후 LLM Outfit Generation에 전달할 카테고리별 후보 경계 객체입니다.
 */
public record RankedClothesCandidates(
    List<Clothes> selectedClothes,
    Map<ClothesCategory, List<RankedClothes>> byCategory
) {
    public RankedClothesCandidates {
        selectedClothes = List.copyOf(selectedClothes);
        Map<ClothesCategory, List<RankedClothes>> input = byCategory;
        byCategory = Arrays.stream(ClothesCategory.values()).collect(java.util.stream.Collectors.toUnmodifiableMap(
            category -> category,
            category -> List.copyOf(input.getOrDefault(category, List.of()))
        ));
    }

    public static RankedClothesCandidates empty() {
        return of(List.of(), Map.of());
    }

    public static RankedClothesCandidates of(
        List<Clothes> selectedClothes,
        Map<ClothesCategory, List<RankedClothes>> byCategory
    ) {
        return new RankedClothesCandidates(selectedClothes, byCategory);
    }

    public List<RankedClothes> category(ClothesCategory category) {
        return byCategory.get(category);
    }

    public Stream<RankedClothes> all() {
        return byCategory.values().stream().flatMap(List::stream);
    }

    public boolean isRecommendable() {
        boolean selectedHasTop = hasSelectedRole(ClothesRole.TOP);
        boolean selectedHasBottom = hasSelectedRole(ClothesRole.BOTTOM);
        boolean selectedHasDress = hasSelectedRole(ClothesRole.ONE_PIECE);
        boolean availableTop = selectedHasTop || !category(ClothesCategory.TOP).isEmpty();
        boolean availableBottom = selectedHasBottom
            || !category(ClothesCategory.PANTS).isEmpty()
            || !category(ClothesCategory.SKIRT).isEmpty();
        boolean availableDress = selectedHasDress || !category(ClothesCategory.DRESS).isEmpty();
        return (availableTop && availableBottom) || availableDress;
    }

    /**
     * 선택 의상 카테고리로 고정된 코디 역할 보유 여부를 판단한다.
     */
    private boolean hasSelectedRole(ClothesRole role) {
        return selectedClothes.stream().anyMatch(clothes -> ClothesRole.from(clothes.getCategory()) == role);
    }
}
