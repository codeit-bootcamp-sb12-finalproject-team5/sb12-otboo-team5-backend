package com.codeit.otboo.domain.user.entity;

import com.codeit.otboo.domain.common.BaseEntity;

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
@Table(name = "user_oauth_link")
@Getter @SuperBuilder @ToString(callSuper = true, exclude = "user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOauthLink extends BaseEntity {

	@Column(name = "provider", length = 20, nullable = false)
	private String provider;

	@Column(name = "provider_id", length = 255, nullable = false)
	private String providerId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

}
