package com.codeit.otboo.domain.recommendation.entity;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "recommendation_outfit_clothes",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_recommendation_outfit_clothes",
        columnNames = {"recommendation_outfit_history_id", "clothes_id"}
    )
)
@Getter
@SuperBuilder
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationOutfitClothes extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_outfit_history_id", nullable = false)
    private RecommendationOutfitHistory recommendationOutfitHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clothes_id", nullable = false)
    private Clothes clothes;

    public RecommendationOutfitClothes(RecommendationOutfitHistory recommendationOutfitHistory, Clothes clothes) {
        this.recommendationOutfitHistory = recommendationOutfitHistory;
        this.clothes = clothes;
    }
}
