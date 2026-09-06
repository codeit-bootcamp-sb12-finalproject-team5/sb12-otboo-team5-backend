package com.codeit.otboo.domain.follow.entity;

import com.codeit.otboo.domain.common.BaseEntity;
import com.codeit.otboo.domain.user.entity.User;

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
@Table(name = "follow")
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "follower_id", nullable = false)
	private User follower;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "followee_id", nullable = false)
	private User followee;


	public static Follow create(User follower, User followee) {
		if (follower.getId().equals(followee.getId())) {
			throw new IllegalArgumentException("자기 자신을 팔로우할 수 없습니다.");
		}
		return Follow.builder()
			.follower(follower)
			.followee(followee)
			.build();
	}

}
