package com.codeit.otboo.worker.notification.repository;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.dto.NotificationContent;
import com.fasterxml.uuid.Generators;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationInsertRepository {
    private final JdbcTemplate jdbc;

    public NotificationInsertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean receiverExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE id = ? AND deleted_at IS NULL)",
                Boolean.class, id));
    }

    public Optional<NotificationDto> insert(
            NotificationType type, String deduplicationKey, NotificationContent payload
    ) {
        return jdbc.query("""
                INSERT INTO notification
                    (id, created_at, receiver_id, title, content, level, type, deduplication_key)
                SELECT ?, ?, id, ?, ?, ?, ?, ?
                FROM users WHERE id = ? AND deleted_at IS NULL
                ON CONFLICT (receiver_id, deduplication_key) DO NOTHING
                RETURNING id, created_at, receiver_id, title, content, level
                """, (rs, row) -> new NotificationDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("receiver_id", UUID.class),
                        rs.getString("title"), rs.getString("content"),
                        NotificationLevel.valueOf(rs.getString("level"))),
                Generators.timeBasedEpochGenerator().generate(), OffsetDateTime.now(ZoneOffset.UTC),
                payload.title(), payload.content(), payload.level().name(), type.name(),
                deduplicationKey, payload.receiverId()).stream().findFirst();
    }
}
