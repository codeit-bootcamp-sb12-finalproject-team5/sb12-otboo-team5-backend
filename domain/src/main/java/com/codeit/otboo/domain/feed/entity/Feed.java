package com.codeit.otboo.domain.feed.entity;

import com.codeit.otboo.domain.common.SoftDeletableEntity;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "feed")
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed extends SoftDeletableEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(
		name = "id",
		referencedColumnName = "id",
		insertable = false,
		updatable = false
	)
	private Outfit outfit;

	@Column(name = "content", columnDefinition = "TEXT")
	private String content;

	@Column(name = "is_visible", nullable = false)
	private boolean isVisible;

	@Column(name = "like_count", nullable = false)
	private long likeCount;

	@Column(name = "comment_count", nullable = false)
	private long commentCount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

}
