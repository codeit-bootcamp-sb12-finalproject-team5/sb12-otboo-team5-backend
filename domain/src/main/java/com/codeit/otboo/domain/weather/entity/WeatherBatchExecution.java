package com.codeit.otboo.domain.weather.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
@Table(
	name = "weather_batch_execution",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_weather_batch_job_date",
		columnNames = {"job_name", "target_date"}
	)
)
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherBatchExecution extends UpdatableEntity {

	@Column(name = "job_name", nullable = false, length = 100)
	private String jobName;

	@Column(name = "target_date", nullable = false)
	private LocalDate targetDate;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "total_grid_count", nullable = false)
	private int totalGridCount;

	@Column(name = "success_grid_count", nullable = false)
	private int successGridCount;

	@Column(name = "failed_grid_count", nullable = false)
	private int failedGridCount;

	@Column(name = "started_at", nullable = false)
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;


	public void restart(int totalGridCount, OffsetDateTime startedAt) {
		this.status = "RUNNING";
		this.totalGridCount = totalGridCount;
		this.successGridCount = 0;
		this.failedGridCount = 0;
		this.startedAt = startedAt;
		this.completedAt = null;
		this.errorMessage = null;
	}

	public void complete(
		String status,
		int successGridCount,
		int failedGridCount,
		OffsetDateTime completedAt,
		String errorMessage
	) {
		this.status = status;
		this.successGridCount = successGridCount;
		this.failedGridCount = failedGridCount;
		this.completedAt = completedAt;
		this.errorMessage = errorMessage;
	}

}
