package com.codeit.otboo.domain.dm.entity;

import java.time.OffsetDateTime;

import com.codeit.otboo.domain.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "dm_room")
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmRoom extends BaseEntity {

	@Column(name = "dm_key", nullable = false, unique = true, length = 73)
	private String dmKey;

	@Column(name = "last_message_at")
	private OffsetDateTime lastMessageAt;


	public DmRoom(String dmKey) {
		this.dmKey = dmKey;
	}

	public void updateLastMessageAt(OffsetDateTime lastMessageAt) {
		this.lastMessageAt = lastMessageAt;
	}

}
