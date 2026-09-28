package com.codeit.otboo.domain.outfit.entity;

import com.codeit.otboo.domain.common.SoftDeletableEntity;
import com.codeit.otboo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
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

	@Setter
	@Column(name = "image_key", length = 1000)
	private String imageKey;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;


	public Outfit(
		User user,
		String name,
		String category,
		String description
	) {
		this.user = user;
		this.name = name;
		this.category = category;
		this.description = description;
	}

	public void update(String name, String category, String description) {
		if (name != null) {
			this.name = name;
		}

		if (category != null && !category.equals("OOTD") && !this.category.equals("OOTD")) {
			this.category = category;
		}

		if (description != null) {
			this.description = description;
		}

		touch();
	}

	public void updateImageKey(String imageKey) {
		this.imageKey = imageKey;
		touch();
	}
}
