package com.codeit.otboo.worker.notification.repository;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.dto.NotificationContent;
import com.fasterxml.uuid.Generators;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public List<NotificationDto> insertPage(NotificationType type, String deduplicationKey,
            List<UUID> receivers, String title, String content, NotificationLevel level) {
        if (receivers.isEmpty()) {
            return List.of();
        }

        String values = String.join(", ", Collections.nCopies(receivers.size(), "(?::uuid, ?::uuid)"));
        List<Object> parameters = new ArrayList<>(receivers.size() * 2 + 6);
        parameters.add(OffsetDateTime.now(ZoneOffset.UTC));
        parameters.add(title);
        parameters.add(content);
        parameters.add(level.name());
        parameters.add(type.name());
        parameters.add(deduplicationKey);
        for (UUID receiver : receivers) {
            parameters.add(Generators.timeBasedEpochGenerator().generate());
            parameters.add(receiver);
        }

        return jdbc.query("""
                INSERT INTO notification
                    (id, created_at, receiver_id, title, content, level, type, deduplication_key)
                SELECT batch.id, ?, batch.receiver_id, ?, ?, ?, ?, ?
                FROM (VALUES %s) AS batch(id, receiver_id)
                JOIN users u ON u.id = batch.receiver_id AND u.deleted_at IS NULL
                ON CONFLICT (receiver_id, deduplication_key) DO NOTHING
                RETURNING id, created_at, receiver_id, title, content, level
                """.formatted(values), (rs, row) -> new NotificationDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("receiver_id", UUID.class),
                        rs.getString("title"), rs.getString("content"),
                        NotificationLevel.valueOf(rs.getString("level"))), parameters.toArray());
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
