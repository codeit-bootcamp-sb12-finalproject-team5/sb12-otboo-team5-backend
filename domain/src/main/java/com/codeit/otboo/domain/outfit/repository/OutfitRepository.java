package com.codeit.otboo.domain.outfit.repository;

import com.codeit.otboo.domain.outfit.entity.Outfit;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutfitRepository extends JpaRepository<Outfit, UUID> {

    Optional<Outfit> findByIdAndDeletedAtIsNull(UUID outfitId);
}
