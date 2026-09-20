package com.codeit.otboo.api.recommendation.ranking;

import java.util.List;

/** 이후 LLM Outfit Generation에 전달할 카테고리별 후보 경계 객체입니다. */
public record RankedClothesCandidates(
    List<RankedClothes> tops,
    List<RankedClothes> bottoms,
    List<RankedClothes> outers,
    List<RankedClothes> shoes
) {
    public static RankedClothesCandidates empty() {
        return new RankedClothesCandidates(List.of(), List.of(), List.of(), List.of());
    }
}
