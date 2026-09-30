package com.codeit.otboo.domain.clothes.entity;

import com.codeit.otboo.domain.common.BaseEntity;
import com.codeit.otboo.domain.outfit.entity.Outfit;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = "outfit_clothes",
	uniqueConstraints = @UniqueConstraint(
		name = "uq_outfit_clothes",
		columnNames = {"outfit_id", "clothes_id"}
	)
)
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OutfitClothes extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "outfit_id", nullable = false)
	private Outfit outfit;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "clothes_id", nullable = false)
	private Clothes clothes;

}
