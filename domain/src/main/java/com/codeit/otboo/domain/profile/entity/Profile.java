package com.codeit.otboo.domain.profile.entity;

import java.time.LocalDate;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.codeit.otboo.domain.common.UpdatableEntity;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "profile")
@Getter @SuperBuilder @ToString(callSuper = true, exclude = {"user", "weatherGrid"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends UpdatableEntity {

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Gender gender;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "location_source", length = 10)
	private LocationSource locationSource;

	@Column(name = "temperature_sensitivity", nullable = false)
	private Short temperatureSensitivity = 3;

	@JdbcTypeCode(SqlTypes.VECTOR)
	@Array(length = 768)
	@Column(name = "preference_vector", columnDefinition = "vector(768)")
	private Float[] preferenceVector;

	@Column(name = "profile_image_url", nullable = false, length = 500)
	private String profileImageUrl;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "weather_grid_id")
	private WeatherGrid weatherGrid;


	public void updateGender(Gender gender){
		this.gender = gender;
	}

	public void updateBirthDate(LocalDate birthDate){
		this.birthDate = birthDate;
	}

	public void updateLocation(WeatherGrid weatherGrid, LocationSource locationSource) {
		if (weatherGrid == null || locationSource == null) {
			throw new IllegalArgumentException(
					"WeatherGrid와 위치 출처는 필수입니다."
			);
		}
		this.weatherGrid = weatherGrid;
		this.locationSource = locationSource;
	}

	public void updateTemperatureSensitivity(Short sensitivity){
		if (sensitivity == null || sensitivity < 1 || sensitivity > 5) {
			throw new IllegalArgumentException(
					"더위를 타는 정도는 1~5 사이여야 합니다."
			);
		}
		this.temperatureSensitivity = sensitivity;
	}

	public void updateProfileImageUrl(String profileImageUrl){
		if (profileImageUrl == null || profileImageUrl.isBlank()) {
			throw new IllegalArgumentException(
					"프로필 이미지 URL은 필수입니다."
			);
		}
		this.profileImageUrl = profileImageUrl;
	}

}
