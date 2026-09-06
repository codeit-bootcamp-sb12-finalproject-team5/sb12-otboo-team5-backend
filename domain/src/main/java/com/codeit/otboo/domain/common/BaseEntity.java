package com.codeit.otboo.domain.common;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.uuid.Generators;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
@ToString
@EntityListeners(AuditingEntityListener.class)
@SuperBuilder
public abstract class BaseEntity {

	@Id
	@Column(updatable = false, nullable = false, columnDefinition = "uuid")
	private UUID id;

	@PrePersist
	protected void onCreate() {
		if (id == null) {
			id = Generators.timeBasedEpochGenerator().generate();
		}
	}

	@CreatedDate
	@Column(name = "created_at", updatable = false, nullable = false)
	private OffsetDateTime createdAt;

}
