package com.codeit.otboo.domain.weather.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.codeit.otboo.domain.common.UpdatableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "weather_grid", uniqueConstraints =
	@UniqueConstraint(
		name = "uk_weather_grid_xy", columnNames = {"nx", "ny"}
	)
)
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherGrid extends UpdatableEntity {

	@Column(name = "nx", nullable = false)
	private int nx;

	@Column(name = "ny", nullable = false)
	private int ny;

	@Column(name = "region_1depth", length = 50)
	private String region1Depth;

	@Column(name = "region_2depth", length = 50)
	private String region2Depth;

	@Column(name = "region_3depth", length = 50)
	private String region3Depth;

	@Column(name = "region_4depth", length = 50)
	private String region4Depth;

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	@Column(name = "last_requested_at")
	private OffsetDateTime lastRequestedAt;

	public static WeatherGrid create(int nx, int ny, List<String> locationNames) {
		WeatherGrid entity = new WeatherGrid();
		entity.nx = nx;
		entity.ny = ny;
		entity.enabled = true;
		entity.lastRequestedAt = OffsetDateTime.now();
		entity.updateLocationNames(locationNames);
		return entity;
	}

	public void updateForRequest(List<String> locationNames) {
		updateLocationNames(locationNames);
		lastRequestedAt = OffsetDateTime.now();
	}

	private void updateLocationNames(List<String> locationNames) {
		List<String> padded = new ArrayList<>(locationNames);
		while (padded.size() < 4) padded.add("");
		region1Depth = padded.get(0);
		region2Depth = padded.get(1);
		region3Depth = padded.get(2);
		region4Depth = padded.get(3);
	}

	public List<String> getLocationNames() {
		return List.of(text(region1Depth), text(region2Depth), text(region3Depth), text(region4Depth));
	}

	private String text(String value) {
		return value == null ? "" : value;
	}

}
