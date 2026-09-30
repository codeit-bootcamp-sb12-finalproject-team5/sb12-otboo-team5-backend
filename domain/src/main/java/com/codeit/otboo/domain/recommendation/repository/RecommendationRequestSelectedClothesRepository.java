package com.codeit.otboo.domain.recommendation.repository;

import com.codeit.otboo.domain.recommendation.entity.RecommendationRequestSelectedClothes;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRequestSelectedClothesRepository extends JpaRepository<RecommendationRequestSelectedClothes, UUID> {
}
