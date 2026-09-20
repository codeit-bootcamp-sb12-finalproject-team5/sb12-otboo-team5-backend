package com.codeit.otboo.domain.recommendation.repository;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.recommendation.entity.RecommendationRequestHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRequestHistoryRepository extends JpaRepository<RecommendationRequestHistory, UUID> {

    Page<RecommendationRequestHistory> findByUser_IdAndRecommendationTypeOrderByRequestedAtDesc(
        UUID userId,
        RecommendationType recommendationType,
        Pageable pageable
    );
}
