package com.codeit.otboo.domain.outfit.entity;

import com.codeit.otboo.domain.common.SoftDeletableEntity;
import com.codeit.otboo.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "outfit")
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outfit extends SoftDeletableEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 100)
	private String category;

	@Column(columnDefinition = "TEXT")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;


	public Outfit(
		User user,
		String name,
		String description
	) {
		this.user = user;
		this.name = name;
		this.description = description;
	}
}
