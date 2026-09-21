package com.codeit.otboo.domain.recommendation.entity;

import com.codeit.otboo.domain.common.BaseEntity;
import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "recommendation_request_history")
@Getter
@SuperBuilder
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRequestHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 20)
    private RecommendationType recommendationType;

    @Column(name = "weather_id", nullable = false, columnDefinition = "uuid")
    private UUID weatherId;

    @Column(name = "algorithm_version", nullable = false, length = 100)
    private String algorithmVersion;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    public RecommendationRequestHistory(
        User user,
        RecommendationType recommendationType,
        UUID weatherId,
        String algorithmVersion,
        String promptVersion,
        OffsetDateTime requestedAt
    ) {
        this.user = user;
        this.recommendationType = recommendationType;
        this.weatherId = weatherId;
        this.algorithmVersion = algorithmVersion;
        this.promptVersion = promptVersion;
        this.requestedAt = requestedAt;
    }
}
