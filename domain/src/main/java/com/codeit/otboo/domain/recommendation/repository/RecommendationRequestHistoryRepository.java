package com.codeit.otboo.domain.recommendation.repository;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.recommendation.entity.RecommendationRequestHistory;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRequestHistoryRepository extends JpaRepository<RecommendationRequestHistory, UUID> {

    Page<RecommendationRequestHistory> findByUser_IdAndRecommendationTypeOrderByRequestedAtDesc(
        UUID userId,
        RecommendationType recommendationType,
        Pageable pageable
    );

    @Query("""
        select count(history) from RecommendationRequestHistory history
        where history.user.id = :userId
          and history.recommendationType = :recommendationType
          and history.requestedAt >= :startInclusive
          and history.requestedAt < :endExclusive
        """)
    long countByUserAndTypeAndRequestedAtBetween(
        @Param("userId") UUID userId,
        @Param("recommendationType") RecommendationType recommendationType,
        @Param("startInclusive") OffsetDateTime startInclusive,
        @Param("endExclusive") OffsetDateTime endExclusive
    );
}
