package com.codeit.otboo.domain.recommendation.repository;

import com.codeit.otboo.domain.recommendation.entity.RecommendationOutfitClothes;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationOutfitClothesRepository extends JpaRepository<RecommendationOutfitClothes, UUID> {

    List<RecommendationOutfitClothes> findAllByRecommendationOutfitHistory_Id(UUID outfitHistoryId);
}
