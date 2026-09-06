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
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "weather_forecast", uniqueConstraints =
	@UniqueConstraint(name = "uk_weather_forecast_slot",
	columnNames = {"grid_id", "forecast_at"}
	)
)
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherForecast extends UpdatableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "grid_id", nullable = false)
	private WeatherGrid grid;

	@Column(name = "forecasted_at", nullable = false)
	private OffsetDateTime forecastedAt;

	@Column(name = "forecast_at", nullable = false)
	private OffsetDateTime forecastAt;

	@Column(name = "temperature", precision = 6, scale = 2)
	private BigDecimal temperature;

	@Column(name = "humidity", precision = 6, scale = 2)
	private BigDecimal humidity;

	@Column(name = "precipitation_type", length = 20)
	private String precipitationType;

	@Column(name = "precipitation_amount", precision = 8, scale = 2)
	private BigDecimal precipitationAmount;

	@Column(name = "precipitation_probability", precision = 6, scale = 2)
	private BigDecimal precipitationProbability;

	@Column(name = "sky_status", length = 20)
	private String skyStatus;

	@Column(name = "wind_speed", precision = 7, scale = 2)
	private BigDecimal windSpeed;

	@Column(name = "wind_direction", precision = 7, scale = 2)
	private BigDecimal windDirection;

	@Column(name = "min_temperature", precision = 6, scale = 2)
	private BigDecimal minTemperature;

	@Column(name = "max_temperature", precision = 6, scale = 2)
	private BigDecimal maxTemperature;


	public boolean updateIfNewer(WeatherForecast incoming) {
		if (incoming.forecastedAt.isBefore(forecastedAt)) return false;
		forecastedAt = incoming.forecastedAt;
		temperature = incoming.temperature;
		humidity = incoming.humidity;
		precipitationType = incoming.precipitationType;
		precipitationAmount = incoming.precipitationAmount;
		precipitationProbability = incoming.precipitationProbability;
		skyStatus = incoming.skyStatus;
		windSpeed = incoming.windSpeed;
		windDirection = incoming.windDirection;
		minTemperature = incoming.minTemperature;
		maxTemperature = incoming.maxTemperature;
		return true;
	}

}
