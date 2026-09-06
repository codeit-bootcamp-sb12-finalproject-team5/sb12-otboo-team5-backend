package com.codeit.otboo.domain.weather.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.codeit.otboo.domain.common.UpdatableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "weather_observation", uniqueConstraints =
	@UniqueConstraint(name = "uk_weather_observation_slot",
		columnNames = {"grid_id", "observed_at"}
	)
)
@Getter @SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherObservation extends UpdatableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "grid_id", nullable = false)
	private WeatherGrid grid;

	@Column(name = "observed_at", nullable = false)
	private OffsetDateTime observedAt;

	@Column(name = "temperature", precision = 6, scale = 2)
	private BigDecimal temperature;

	@Column(name = "humidity", precision = 6, scale = 2)
	private BigDecimal humidity;

	@Column(name = "precipitation_type", length = 20)
	private String precipitationType;

	@Column(name = "precipitation_amount", precision = 8, scale = 2)
	private BigDecimal precipitationAmount;

	@Column(name = "wind_speed", precision = 7, scale = 2)
	private BigDecimal windSpeed;

	@Column(name = "wind_direction", precision = 7, scale = 2)
	private BigDecimal windDirection;


	public void updateFrom(WeatherObservation incoming) {
		temperature = incoming.temperature;
		humidity = incoming.humidity;
		precipitationType = incoming.precipitationType;
		precipitationAmount = incoming.precipitationAmount;
		windSpeed = incoming.windSpeed;
		windDirection = incoming.windDirection;
	}

}
