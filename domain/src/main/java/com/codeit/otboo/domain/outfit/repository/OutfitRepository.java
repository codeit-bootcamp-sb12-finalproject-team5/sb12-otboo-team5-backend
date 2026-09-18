package com.codeit.otboo.domain.outfit.repository;

import com.codeit.otboo.domain.outfit.entity.Outfit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutfitRepository extends JpaRepository<Outfit, UUID> {

    Optional<Outfit> findByIdAndDeletedAtIsNull(UUID outfitId);

    @Query("""
        select outfit from Outfit outfit
        where outfit.user.id = :userId
          and outfit.deletedAt is null
          and (:cursor is null or outfit.id < :cursor)
        order by outfit.id desc
        """)
    List<Outfit> findAllByUserIdAndCursor(
        @Param("userId") UUID userId,
        @Param("cursor") UUID cursor,
        Pageable pageable
    );

    long countByUser_IdAndDeletedAtIsNull(UUID userId);
}
