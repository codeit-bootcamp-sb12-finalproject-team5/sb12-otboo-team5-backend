package com.codeit.otboo.domain.follow.repository.querydsl;

import com.codeit.otboo.domain.follow.entity.Follow;
import com.codeit.otboo.domain.follow.entity.QFollow;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
public class FollowQueryRepositoryImpl implements FollowQueryRepository {

  private final JPAQueryFactory queryFactory;
  private final QFollow follow = QFollow.follow;

  @Override
  public List<Follow> findFolloweesByFollowerId(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      Sort.Direction direction,
      String nameLike
  ) {
    JPAQuery<Follow> query = queryFactory
        .selectFrom(follow)
        .where(
            follow.follower.id.eq(followerId),
            followeeNameLikeCondition(nameLike),
            cursorCondition(cursor, idAfter, direction)
        );

    if (direction == Sort.Direction.DESC) {
      query.orderBy(
          follow.createdAt.desc(),
          follow.id.desc()
      );
    } else {
      query.orderBy(
          follow.createdAt.asc(),
          follow.id.asc()
      );
    }

    query.limit(limit);

    return query.fetch();
  }

  @Override
  public List<Follow> findFollowersByFolloweeId(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      Sort.Direction direction,
      String nameLike
  ) {
    JPAQuery<Follow> query = queryFactory
        .selectFrom(follow)
        .where(
            follow.followee.id.eq(followeeId),
            followerNameLikeCondition(nameLike),
            cursorCondition(cursor, idAfter, direction)
        );

    if (direction == Sort.Direction.DESC) {
      query.orderBy(
          follow.createdAt.desc(),
          follow.id.desc()
      );
    } else {
      query.orderBy(
          follow.createdAt.asc(),
          follow.id.asc()
      );
    }

    query.limit(limit);

    return query.fetch();
  }

  @Override
  public long countFolloweesByFollowerId(UUID followerId) {
    Long count = queryFactory
        .select(follow.count())
        .from(follow)
        .where(follow.follower.id.eq(followerId))
        .fetchOne();

    return count != null ? count : 0L;
  }

  @Override
  public long countFollowersByFolloweeId(UUID followeeId) {
    Long count = queryFactory
        .select(follow.count())
        .from(follow)
        .where(follow.followee.id.eq(followeeId))
        .fetchOne();

    return count != null ? count : 0L;
  }

  private BooleanExpression followeeNameLikeCondition(String nameLike) {
    if (nameLike == null || nameLike.isBlank()) {
      return null;
    }

    return follow.followee.name.containsIgnoreCase(nameLike);
  }

  private BooleanExpression followerNameLikeCondition(String nameLike) {
    if (nameLike == null || nameLike.isBlank()) {
      return null;
    }

    return follow.follower.name.containsIgnoreCase(nameLike);
  }

  private BooleanExpression cursorCondition(
      String cursor,
      UUID idAfter,
      Sort.Direction direction
  ) {
    if ((cursor == null) != (idAfter == null)) {
      throw new IllegalArgumentException("cursor와 idAfter는 함께 전달되어야 합니다.");
    }

    if (cursor == null) {
      return null;
    }

    OffsetDateTime cursorTime = OffsetDateTime.parse(cursor);

    if (direction == Sort.Direction.DESC) {
      return follow.createdAt.lt(cursorTime)
          .or(
              follow.createdAt.eq(cursorTime)
                  .and(follow.id.lt(idAfter))
          );
    }

    return follow.createdAt.gt(cursorTime)
        .or(
            follow.createdAt.eq(cursorTime)
                .and(follow.id.gt(idAfter))
        );
  }
}
