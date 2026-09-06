package com.codeit.otboo.domain.notification.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.codeit.otboo.domain.common.BaseEntity;
import com.codeit.otboo.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = "notification",
	indexes = @Index(
		name = "idx_notification_receiver_read_created",
		columnList = "receiver_id, read_at, created_at")
)
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 1_000)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationLevel level;

	@Column(name = "read_at")
	private OffsetDateTime readAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receiver_id", nullable = false, updatable = false)
	private User receiver;


	private Notification(
		User receiver,
		String title,
		String content,
		NotificationLevel level) {
		this.receiver = Objects.requireNonNull(receiver);
		this.title = Objects.requireNonNull(title);
		this.content = Objects.requireNonNull(content);
		this.level = Objects.requireNonNull(level);
	}

	public static Notification create(
		User receiver,
		String title,
		String content,
		NotificationLevel level) {
		return new Notification(receiver, title, content, level);
	}

	public void markAsRead() {
		if (this.readAt == null) {
			this.readAt = OffsetDateTime.now();
		}
	}

}
