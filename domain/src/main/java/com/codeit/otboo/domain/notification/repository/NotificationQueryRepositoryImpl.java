package com.codeit.otboo.domain.notification.repository;

import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.notification.entity.Notification;
import com.codeit.otboo.domain.notification.entity.QNotification;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationQueryRepositoryImpl implements NotificationQueryRepository {
    private final JPAQueryFactory queryFactory;

    @Override
    public CursorResponse<Notification> findUnread(
        UUID receiverId,
        OffsetDateTime cursor,
        UUID idAfter,
        int limit
    ) {
        QNotification notification = QNotification.notification;

        BooleanExpression unread = notification.receiver.id.eq(receiverId)
                .and(notification.readAt.isNull());

        var rows = queryFactory.selectFrom(notification)
                .where(unread, cursorCondition(notification, cursor, idAfter))
                .orderBy(notification.createdAt.desc(), notification.id.desc())
                .limit((long) limit + 1)
                .fetch();

        boolean hasNext = rows.size() > limit;

        var data = rows.stream().limit(limit).toList();

        Notification last = hasNext ? data.get(data.size() - 1) : null;

        Long totalCount = queryFactory.select(notification.count()).from(notification)
                .where(unread).fetchOne();

        return new CursorResponse<>(data, last == null ? null : last.getCreatedAt().toString(),
                last == null ? null : last.getId(), hasNext,
                totalCount == null ? 0L : totalCount, "createdAt", "DESCENDING");
    }

    // 알림 id는 UUIDv7이라 생성 순서대로 커진다. 그래서 id 하나로 "그 이후"를 고를 수 있다.
    @Override
    public List<Notification> findUnreadAfter(UUID receiverId, UUID lastEventId, int limit) {
        QNotification notification = QNotification.notification;

        return queryFactory.selectFrom(notification)
                .where(notification.receiver.id.eq(receiverId),
                        notification.readAt.isNull(),
                        notification.id.gt(lastEventId))
                .orderBy(notification.id.asc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression cursorCondition(
        QNotification notification,
        OffsetDateTime cursor,
        UUID idAfter
    ) {
        if (cursor == null) {
            return null;
        }
        return notification.createdAt.lt(cursor)
                .or(notification.createdAt.eq(cursor).and(notification.id.lt(idAfter)));
    }
}
