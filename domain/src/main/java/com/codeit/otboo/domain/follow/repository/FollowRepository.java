package com.codeit.otboo.domain.follow.repository;

import com.codeit.otboo.domain.follow.entity.Follow;
import com.codeit.otboo.domain.follow.repository.querydsl.FollowQueryRepository;
import com.codeit.otboo.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID>, FollowQueryRepository {

  boolean existsByFollowerAndFollowee(User follower, User followee);

  Optional<Follow> findByFollowerAndFollowee(User follower, User followee);
}
