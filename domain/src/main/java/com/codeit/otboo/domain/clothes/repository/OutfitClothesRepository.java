package com.codeit.otboo.domain.clothes.repository;

import com.codeit.otboo.domain.clothes.entity.OutfitClothes;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutfitClothesRepository extends JpaRepository<OutfitClothes, UUID> {

    List<OutfitClothes> findAllByOutfit_Id(UUID outfitId);

    List<OutfitClothes> findAllByOutfit_IdIn(List<UUID> outfitIds);

    @Query("""
        select outfitClothes.clothes.id as clothesId,
            count(outfitClothes.id) as usageCount
        from OutfitClothes outfitClothes
        where outfitClothes.outfit.user.id = :userId
          and outfitClothes.outfit.deletedAt is null
          and outfitClothes.clothes.user.id = :userId
          and outfitClothes.clothes.deletedAt is null
        group by outfitClothes.clothes.id
        """)
    List<OutfitClothesUsageCount> countActiveOutfitUsageByUserId(@Param("userId") UUID userId);

    @Query("""
        select count(outfitClothes.id)
        from OutfitClothes outfitClothes
        where outfitClothes.outfit.user.id = :userId
          and outfitClothes.outfit.deletedAt is null
          and outfitClothes.clothes.id = :clothesId
        """)
    long countActiveOutfitUsageByUserIdAndClothesId(
        @Param("userId") UUID userId,
        @Param("clothesId") UUID clothesId
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from OutfitClothes outfitClothes where outfitClothes.outfit.id = :outfitId")
    void deleteAllByOutfitId(@Param("outfitId") UUID outfitId);
}
