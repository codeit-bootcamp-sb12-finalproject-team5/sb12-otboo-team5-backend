package com.codeit.otboo.domain.clothes.repository;

import java.util.UUID;

public interface OutfitClothesUsageCount {

    UUID getClothesId();

    long getUsageCount();
}
