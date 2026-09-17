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

    @Modifying(flushAutomatically = true)
    @Query("delete from OutfitClothes outfitClothes where outfitClothes.outfit.id = :outfitId")
    void deleteAllByOutfitId(@Param("outfitId") UUID outfitId);
}
