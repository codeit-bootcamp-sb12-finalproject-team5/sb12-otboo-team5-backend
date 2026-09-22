package com.codeit.otboo.domain.feed.repository;

import com.codeit.otboo.domain.feed.entity.FeedLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {
    boolean existsByFeed_IdAndUser_Id(UUID feedId, UUID userId);

    List<FeedLike> findAllByFeed_IdInAndUser_Id(List<UUID> feedIds, UUID userId);

    @Modifying
    @Query("delete from FeedLike feedLike where feedLike.feed.id = :feedId and feedLike.user.id = :userId")
    int deleteByFeedIdAndUserId(@Param("feedId") UUID feedId, @Param("userId") UUID userId);
}
