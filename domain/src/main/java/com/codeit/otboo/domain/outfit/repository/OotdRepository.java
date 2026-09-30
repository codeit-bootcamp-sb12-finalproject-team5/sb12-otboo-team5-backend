package com.codeit.otboo.domain.outfit.repository;

import com.codeit.otboo.domain.outfit.entity.Ootd;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OotdRepository extends JpaRepository<Ootd, UUID> {
    List<Ootd> findAllByOutfit_IdIn(Collection<UUID> outfitIds);
}
