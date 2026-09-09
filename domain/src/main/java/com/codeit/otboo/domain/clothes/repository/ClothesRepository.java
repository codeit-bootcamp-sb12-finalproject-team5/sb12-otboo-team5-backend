package com.codeit.otboo.domain.clothes.repository;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {

}
