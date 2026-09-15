package com.codeit.otboo.worker.notification.repository;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 다른 도메인 Repository를 변경하지 않고 알림에 필요한 원본만 조회한다. */
@Repository
@RequiredArgsConstructor
public class NotificationSourceRepository {
    private final JdbcTemplate jdbc;

    public record Source(UUID receiverId, String actorName, String content) {
    }

    public record FeedSource(UUID authorId, String authorName) {
    }

    public Optional<Source> findComment(UUID id) {
        return source("""
                SELECT f.user_id AS receiver_id, u.name AS actor_name, c.content
                FROM feed_comment c JOIN feed f ON f.id = c.feed_id
                JOIN users u ON u.id = c.user_id
                WHERE c.id = ? AND f.deleted_at IS NULL AND f.is_visible = true AND u.deleted_at IS NULL
                """, id);
    }

    public Optional<Source> findLike(UUID id) {
        return source("""
                SELECT f.user_id AS receiver_id, u.name AS actor_name, '' AS content
                FROM feed_like l JOIN feed f ON f.id = l.feed_id
                JOIN users u ON u.id = l.user_id
                WHERE l.id = ? AND f.deleted_at IS NULL AND f.is_visible = true AND u.deleted_at IS NULL
                """, id);
    }

    public Optional<Source> findFollow(UUID id) {
        return source("""
                SELECT f.followee_id AS receiver_id, u.name AS actor_name, '' AS content
                FROM follow f JOIN users u ON u.id = f.follower_id
                WHERE f.id = ? AND u.deleted_at IS NULL
                """, id);
    }

    public Optional<Source> findDirectMessage(UUID id, UUID receiverId) {
        return source("""
                SELECT member.user_id AS receiver_id, sender.name AS actor_name, m.content
                FROM direct_message m JOIN users sender ON sender.id = m.sender_id
                JOIN dm_room_member member ON member.dm_room_id = m.dm_room_id
                WHERE m.id = ? AND member.user_id = ? AND member.user_id <> m.sender_id
                  AND member.left_at IS NULL AND member.joined_at <= m.created_at
                  AND sender.deleted_at IS NULL
                """, id, receiverId);
    }

    public Optional<FeedSource> findFeed(UUID id) {
        return jdbc.query("""
                SELECT f.user_id, u.name FROM feed f JOIN users u ON u.id = f.user_id
                WHERE f.id = ? AND f.deleted_at IS NULL AND f.is_visible = true AND u.deleted_at IS NULL
                """, (rs, row) -> new FeedSource(rs.getObject("user_id", UUID.class), rs.getString("name")),
                id).stream().findFirst();
    }

    private Optional<Source> source(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> new Source(rs.getObject("receiver_id", UUID.class),
                rs.getString("actor_name"), rs.getString("content")), args).stream().findFirst();
    }
}
