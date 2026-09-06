package com.codeit.otboo.domain.user.entity;

import java.time.OffsetDateTime;

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
@Table(name = "refresh_token")
@Getter @SuperBuilder @ToString(callSuper = true, exclude = "user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

	@Column(name = "token", length = 500, nullable = false)
	private String token;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

}
