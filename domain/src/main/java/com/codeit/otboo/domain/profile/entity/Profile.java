package com.codeit.otboo.domain.profile.entity;

import java.time.LocalDate;

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
	@Column(name = "preference_vector", nullable = false)
	private float[] preferenceVector;

	@Column(name = "profile_image_url", nullable = false, length = 500)
	private String profileImageUrl;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "weather_grid_id", nullable = false)
	private WeatherGrid weatherGrid;

}
