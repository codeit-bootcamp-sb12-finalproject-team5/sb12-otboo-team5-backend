package com.codeit.otboo.domain.recommendation.repository;

import com.codeit.otboo.domain.recommendation.entity.RecommendationOutfitHistory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationOutfitHistoryRepository extends JpaRepository<RecommendationOutfitHistory, UUID> {

    List<RecommendationOutfitHistory> findAllByRecommendationRequestHistory_IdOrderByRankAsc(UUID requestHistoryId);

    List<RecommendationOutfitHistory> findAllByRecommendationRequestHistory_IdIn(Collection<UUID> requestHistoryIds);
}
