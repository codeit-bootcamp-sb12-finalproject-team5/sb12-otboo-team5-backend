package com.codeit.otboo.domain.feed.repository;

import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.feed.entity.Feed;
import com.codeit.otboo.domain.feed.entity.QFeed;
import com.codeit.otboo.domain.feed.enums.PrecipitationType;
import com.codeit.otboo.domain.feed.enums.SkyStatus;
import com.codeit.otboo.domain.feed.enums.SortDirection;
import com.codeit.otboo.domain.feed.exception.FeedException;
import com.codeit.otboo.domain.outfit.entity.QOotd;
import com.codeit.otboo.domain.outfit.entity.QOutfit;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FeedQueryRepositoryImpl implements FeedQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public CursorResponse<Feed> findAllByDynamicQuery(
        String cursor,
        UUID idAfter,
        Integer limit,
        String sortBy,
        SortDirection sortDirection,
        String keywordLike,
        SkyStatus skyStatusEqual,
        PrecipitationType precipitationTypeEqual,
        UUID authorIdEqual
    ) {
        QFeed feed = QFeed.feed;
        QOutfit outfit = QOutfit.outfit;
        QOotd ootd = QOotd.ootd;

        List<Feed> content = new ArrayList<>(queryFactory
            .selectFrom(feed)
            .join(feed.outfit, outfit).fetchJoin()
            .join(feed.user).fetchJoin()
            .leftJoin(ootd).on(ootd.outfit.eq(outfit))
            .where(
                feed.deletedAt.isNull(),
                feed.isVisible.isTrue(),
                outfit.deletedAt.isNull(),
                keywordLikeContains(feed, keywordLike),
                skyStatusEquals(ootd, skyStatusEqual),
                precipitationTypeEquals(ootd, precipitationTypeEqual),
                authorIdEquals(feed, authorIdEqual),
                cursorCondition(feed, cursor, idAfter, sortBy, sortDirection)
            )
            .orderBy(orderBy(feed, sortBy, sortDirection))
            .limit(limit + 1L)
            .fetch());

        boolean hasNext = content.size() > limit;
        if (hasNext) {
            content.remove(content.size() - 1);
        }

        Feed last = hasNext ? content.get(content.size() - 1) : null;
        String nextCursor = last == null ? null : nextCursor(last, sortBy);
        UUID nextIdAfter = last == null ? null : last.getId();

        Long totalCount = queryFactory
            .select(feed.count())
            .from(feed)
            .join(feed.outfit, outfit)
            .leftJoin(ootd).on(ootd.outfit.eq(outfit))
            .where(
                feed.deletedAt.isNull(),
                feed.isVisible.isTrue(),
                outfit.deletedAt.isNull(),
                keywordLikeContains(feed, keywordLike),
                skyStatusEquals(ootd, skyStatusEqual),
                precipitationTypeEquals(ootd, precipitationTypeEqual),
                authorIdEquals(feed, authorIdEqual)
            )
            .fetchOne();

        return CursorResponse.of(
            content, nextCursor, nextIdAfter, hasNext, totalCount == null ? 0L : totalCount,
            sortBy, sortDirection.name()
        );
    }

    private BooleanExpression keywordLikeContains(QFeed feed, String keywordLike) {
        return keywordLike == null || keywordLike.isBlank() ? null : feed.content.containsIgnoreCase(keywordLike);
    }

    private BooleanExpression skyStatusEquals(QOotd ootd, SkyStatus skyStatusEqual) {
        return skyStatusEqual == null ? null : ootd.skyStatus.eq(skyStatusEqual);
    }

    private BooleanExpression precipitationTypeEquals(QOotd ootd, PrecipitationType precipitationTypeEqual) {
        return precipitationTypeEqual == null ? null : ootd.precipitationType.eq(precipitationTypeEqual);
    }

    private BooleanExpression authorIdEquals(QFeed feed, UUID authorIdEqual) {
        return authorIdEqual == null ? null : feed.user.id.eq(authorIdEqual);
    }

    private BooleanExpression cursorCondition(
        QFeed feed, String cursor, UUID idAfter, String sortBy, SortDirection sortDirection
    ) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        return "createdAt".equals(sortBy)
            ? createdAtCursorCondition(feed, parseCreatedAt(cursor), idAfter, sortDirection)
            : likeCountCursorCondition(feed, parseLikeCount(cursor), idAfter, sortDirection);
    }

    private BooleanExpression createdAtCursorCondition(
        QFeed feed, OffsetDateTime cursor, UUID idAfter, SortDirection sortDirection
    ) {
        BooleanExpression primary = sortDirection == SortDirection.ASCENDING
            ? feed.createdAt.gt(cursor) : feed.createdAt.lt(cursor);
        if (idAfter == null) {
            return primary;
        }
        BooleanExpression tieBreaker = sortDirection == SortDirection.ASCENDING
            ? feed.id.gt(idAfter) : feed.id.lt(idAfter);
        return primary.or(feed.createdAt.eq(cursor).and(tieBreaker));
    }

    private BooleanExpression likeCountCursorCondition(
        QFeed feed, long cursor, UUID idAfter, SortDirection sortDirection
    ) {
        BooleanExpression primary = sortDirection == SortDirection.ASCENDING
            ? feed.likeCount.gt(cursor) : feed.likeCount.lt(cursor);
        if (idAfter == null) {
            return primary;
        }
        BooleanExpression tieBreaker = sortDirection == SortDirection.ASCENDING
            ? feed.id.gt(idAfter) : feed.id.lt(idAfter);
        return primary.or(feed.likeCount.eq(cursor).and(tieBreaker));
    }

    private OrderSpecifier<?>[] orderBy(QFeed feed, String sortBy, SortDirection sortDirection) {
        boolean ascending = sortDirection == SortDirection.ASCENDING;
        if ("createdAt".equals(sortBy)) {
            return ascending
                ? new OrderSpecifier<?>[]{feed.createdAt.asc(), feed.id.asc()}
                : new OrderSpecifier<?>[]{feed.createdAt.desc(), feed.id.desc()};
        }
        return ascending
            ? new OrderSpecifier<?>[]{feed.likeCount.asc(), feed.id.asc()}
            : new OrderSpecifier<?>[]{feed.likeCount.desc(), feed.id.desc()};
    }

    private String nextCursor(Feed feed, String sortBy) {
        return "createdAt".equals(sortBy)
            ? feed.getCreatedAt().toString()
            : Long.toString(feed.getLikeCount());
    }

    private OffsetDateTime parseCreatedAt(String cursor) {
        try {
            return OffsetDateTime.parse(cursor);
        } catch (DateTimeParseException exception) {
            throw new FeedException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private long parseLikeCount(String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw new FeedException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
