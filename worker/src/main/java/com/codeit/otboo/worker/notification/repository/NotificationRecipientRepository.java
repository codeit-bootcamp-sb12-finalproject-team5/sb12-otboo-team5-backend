package com.codeit.otboo.worker.notification.repository;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRecipientRepository {
    private final JdbcTemplate jdbc;

    public List<UUID> findGridUsers(UUID gridId, UUID after, int limit) {
        return jdbc.query("""
                SELECT u.id FROM users u JOIN profile p ON p.user_id = u.id
                WHERE p.weather_grid_id = ? AND u.deleted_at IS NULL
                  AND (CAST(? AS UUID) IS NULL OR u.id > CAST(? AS UUID))
                ORDER BY u.id LIMIT ?
                """, (rs, row) -> rs.getObject("id", UUID.class), gridId, after, after, limit);
    }

    public List<UUID> findFollowers(UUID authorId, UUID after, int limit) {
        return jdbc.query("""
                SELECT u.id FROM users u JOIN follow f ON f.follower_id = u.id
                WHERE f.followee_id = ? AND u.deleted_at IS NULL AND u.id <> ?
                  AND (CAST(? AS UUID) IS NULL OR u.id > CAST(? AS UUID))
                ORDER BY u.id LIMIT ?
                """, (rs, row) -> rs.getObject("id", UUID.class), authorId, authorId, after, after, limit);
    }
}
