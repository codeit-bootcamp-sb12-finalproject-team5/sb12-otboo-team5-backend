package com.codeit.otboo.domain.feed.repository;

import com.codeit.otboo.domain.feed.entity.FeedComment;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface FeedCommentRepository extends JpaRepository<FeedComment, UUID> {

    @Query("""
        select comment from FeedComment comment
        join fetch comment.user
        where comment.feed.id = :feedId
          and (
            :cursor is null
            or comment.createdAt < :cursor
            or (:idAfter is not null and comment.createdAt = :cursor and comment.id < :idAfter)
          )
        order by comment.createdAt desc, comment.id desc
        """)
    List<FeedComment> findAllByFeedIdAndCursor(
        @Param("feedId") UUID feedId,
        @Param("cursor") OffsetDateTime cursor,
        @Param("idAfter") UUID idAfter,
        Pageable pageable
    );

    long countByFeed_Id(UUID feedId);
}
