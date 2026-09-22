package com.codeit.otboo.domain.follow.repository.querydsl;

import com.codeit.otboo.domain.follow.entity.Follow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;

public interface FollowQueryRepository {

  List<Follow> findFolloweesByFollowerId(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      Sort.Direction direction
  );

  List<Follow> findFollowersByFolloweeId(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      Sort.Direction direction
  );

  long countFolloweesByFollowerId(UUID followerId);
  long countFollowersByFolloweeId(UUID followeeId);
}
