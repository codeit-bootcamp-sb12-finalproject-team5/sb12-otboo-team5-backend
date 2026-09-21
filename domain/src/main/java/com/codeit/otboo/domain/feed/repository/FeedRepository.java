package com.codeit.otboo.domain.feed.repository;

import com.codeit.otboo.domain.feed.entity.Feed;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FeedRepository extends JpaRepository<Feed, UUID>, FeedQueryRepository {

    Optional<Feed> findByIdAndDeletedAtIsNull(UUID feedId);

    @Modifying
    @Query("update Feed feed set feed.likeCount = feed.likeCount + 1 where feed.id = :feedId and feed.deletedAt is null")
    int incrementLikeCount(@Param("feedId") UUID feedId);

    @Modifying
    @Query("""
        update Feed feed
        set feed.likeCount = feed.likeCount - 1
        where feed.id = :feedId and feed.deletedAt is null and feed.likeCount > 0
        """)
    int decrementLikeCount(@Param("feedId") UUID feedId);

    @Modifying
    @Query("update Feed feed set feed.commentCount = feed.commentCount + 1 where feed.id = :feedId and feed.deletedAt is null")
    int incrementCommentCount(@Param("feedId") UUID feedId);

}
