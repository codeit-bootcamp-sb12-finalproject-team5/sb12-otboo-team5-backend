package com.codeit.otboo.domain.user.entity;

import com.codeit.otboo.domain.common.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
		name = "user_oauth_link",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_user_oauth_link_provider_id",
						columnNames = {"provider", "provider_id"}
				)
		}
)
@Getter @SuperBuilder @ToString(callSuper = true, exclude = "user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOauthLink extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", length = 20, nullable = false)
	private OAuthProvider provider;

	@Column(name = "provider_id", length = 255, nullable = false)
	private String providerId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

}
