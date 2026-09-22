package com.codeit.otboo.domain.clothes.repository;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesRepository extends JpaRepository<Clothes, UUID>, ClothesQueryRepository {

    List<Clothes> findAllByIdInAndUser_IdAndDeletedAtIsNull(Collection<UUID> clothesIds, UUID userId);

    List<Clothes> findAllByIdInAndDeletedAtIsNull(Collection<UUID> clothesIds);

    List<Clothes> findAllByUser_IdAndDeletedAtIsNull(UUID userId);

}
