package com.codeit.otboo.api.recommendation.candidate;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import java.util.List;
import java.util.UUID;

/** 추천 유형에 맞는, 필터링 전 Clothes 후보를 DB에서 조회합니다. */
public interface ClothesCandidateProvider {

    RecommendationType getType();

    List<Clothes> findCandidates(UUID userId);
}
