package com.codeit.otboo.api.outfit;

import com.codeit.otboo.api.recommendation.preference.PreferenceVectorAsyncService;
import com.codeit.otboo.api.recommendation.temperature.TemperatureAdjustmentPolicy;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.outfit.repository.OotdRepository;
import com.codeit.otboo.domain.outfit.repository.OutfitRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.support.llm.outfit.dto.GeneratedOutfitImage;
import com.codeit.otboo.support.storage.S3StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutfitServiceImageTest {

    private final OutfitRepository outfitRepository = mock(OutfitRepository.class);
    private final S3StorageService storageService = mock(S3StorageService.class);
    private final OutfitService outfitService = new OutfitService(
            outfitRepository,
            mock(OutfitClothesRepository.class),
            mock(ClothesRepository.class),
            mock(UserRepository.class),
            mock(WeatherForecastRepository.class),
            mock(WeatherRepository.class),
            mock(OotdRepository.class),
            mock(TemperatureAdjustmentPolicy.class),
            storageService,
            mock(PreferenceVectorAsyncService.class)
    );

    @Test
    void deletesExistingGeneratedImageBeforeStoringNewGeneratedImage() {
        UUID outfitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Outfit outfit = ownedOutfit(outfitId, userId, "outfits/old/generated.png");
        GeneratedOutfitImage image = new GeneratedOutfitImage(new byte[]{1, 2}, "image/png", "model", "prompt");
        when(storageService.saveOutfit(image.imageBytes(), image.mimeType(), outfitId))
                .thenReturn("outfits/new/generated.png");
        when(storageService.getPresignedUrl("outfits/new/generated.png"))
                .thenReturn("https://storage.example/new.png");

        var response = outfitService.createImage(image, outfitId, userId);

        assertThat(response.imageUrl()).isEqualTo("https://storage.example/new.png");
        var order = inOrder(storageService);
        order.verify(storageService).deleteOne("outfits/old/generated.png");
        order.verify(storageService).saveOutfit(image.imageBytes(), image.mimeType(), outfitId);
        verify(outfit).updateImageKey("outfits/new/generated.png");
    }

    @Test
    void deletesExistingUploadedImageBeforeStoringNewUpload() {
        UUID outfitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Outfit outfit = ownedOutfit(outfitId, userId, "outfits/old/original.png");
        MockMultipartFile image = new MockMultipartFile("image", "new.jpg", "image/jpeg", new byte[]{1, 2});
        when(storageService.saveOutfit(image, outfitId)).thenReturn("outfits/new/original.jpg");
        when(storageService.getPresignedUrl("outfits/new/original.jpg"))
                .thenReturn("https://storage.example/new.jpg");

        outfitService.createImage(image, outfitId, userId);

        var order = inOrder(storageService);
        order.verify(storageService).deleteOne("outfits/old/original.png");
        order.verify(storageService).saveOutfit(image, outfitId);
        verify(outfit).updateImageKey("outfits/new/original.jpg");
    }

    private Outfit ownedOutfit(UUID outfitId, UUID userId, String imageKey) {
        Outfit outfit = mock(Outfit.class);
        User owner = mock(User.class);
        when(outfitRepository.findByIdAndDeletedAtIsNull(outfitId)).thenReturn(Optional.of(outfit));
        when(outfit.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(userId);
        when(outfit.getImageKey()).thenReturn(imageKey);
        return outfit;
    }
}
