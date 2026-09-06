package com.codeit.otboo.domain.outfit.entity;

import java.math.BigDecimal;

import com.codeit.otboo.domain.common.UpdatableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ootd")
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ootd extends UpdatableEntity {

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "outfit_id")
	private Outfit outfit;

	@Column(name = "sky_status", length = 20, nullable = false)
	private String skyStatus;

	@Column(name = "precipitation_type", length = 20, nullable = false)
	private String precipitationType;

	@Column(name = "precipitation_amount", precision = 8, scale = 2, nullable = false)
	private BigDecimal precipitationAmount;

	@Column(name = "precipitation_probability", precision = 6, scale = 2, nullable = false)
	private BigDecimal precipitationProbability;

	@Column(name = "temperature_current", precision = 6, scale = 2, nullable = false)
	private BigDecimal temperatureCurrent;

	@Column(name = "temperature_compared_to_day_before", precision = 6, scale = 2)
	private BigDecimal temperatureComparedToDayBefore;

	@Column(name = "temperature_min", precision = 6, scale = 2, nullable = false)
	private BigDecimal temperatureMin;

	@Column(name = "temperature_max", precision = 6, scale = 2, nullable = false)
	private BigDecimal temperatureMax;

}
