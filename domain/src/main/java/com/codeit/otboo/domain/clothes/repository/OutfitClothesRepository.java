package com.codeit.otboo.domain.clothes.repository;

import com.codeit.otboo.domain.clothes.entity.OutfitClothes;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutfitClothesRepository extends JpaRepository<OutfitClothes, UUID> {
}
