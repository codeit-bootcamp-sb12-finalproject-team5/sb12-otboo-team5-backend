package com.codeit.otboo.domain.user.entity;

import java.time.OffsetDateTime;

import com.codeit.otboo.domain.common.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Getter @Setter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends SoftDeletableEntity {

	@Column(name = "email", nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@Column(name = "password", nullable = false, length = 60)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private UserRole role;

	@Column(name = "locked", nullable = false)
	private Boolean locked;

	@Column(name = "token_version", nullable = false)
	private Integer tokenVersion;

	@Column(name = "temp_password", length = 60)
	private String tempPassword;

	@Column(name = "temp_password_expires_at")
	private OffsetDateTime tempPasswordExpiresAt;

}
