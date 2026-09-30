package com.codeit.otboo.domain.common;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
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
public abstract class SoftDeletableEntity extends UpdatableEntity{

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	public void markDeleted() {
		this.deletedAt = OffsetDateTime.now();
	}

	public boolean isDeleted() {
		return this.deletedAt != null;
	}

}
