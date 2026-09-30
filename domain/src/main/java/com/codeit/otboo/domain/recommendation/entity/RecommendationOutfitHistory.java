package com.codeit.otboo.domain.recommendation.entity;

import com.codeit.otboo.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "recommendation_outfit_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_recommendation_outfit_history_request_fingerprint",
        columnNames = {"recommendation_request_history_id", "outfit_fingerprint"}
    )
)
@Getter
@SuperBuilder
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationOutfitHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_request_history_id", nullable = false)
    private RecommendationRequestHistory recommendationRequestHistory;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "outfit_fingerprint", nullable = false, length = 64)
    private String outfitFingerprint;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "exposed_at", nullable = false)
    private OffsetDateTime exposedAt;

    public RecommendationOutfitHistory(
        RecommendationRequestHistory recommendationRequestHistory,
        Integer rank,
        String outfitFingerprint,
        String reason,
        OffsetDateTime exposedAt
    ) {
        this.recommendationRequestHistory = recommendationRequestHistory;
        this.rank = rank;
        this.outfitFingerprint = outfitFingerprint;
        this.reason = reason;
        this.exposedAt = exposedAt;
    }
}
