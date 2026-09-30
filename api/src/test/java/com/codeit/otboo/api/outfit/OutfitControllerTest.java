package com.codeit.otboo.api.outfit;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.outfit.dto.OutfitImageResponse;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.support.llm.outfit.OutfitImageGenerateService;
import com.codeit.otboo.support.llm.outfit.dto.GeneratedOutfitImage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutfitControllerTest {

    @Test
    void generatesFittingImageAndReturnsStoredImageUrl() {
        OutfitService outfitService = mock(OutfitService.class);
        OutfitImageGenerateService generator = mock(OutfitImageGenerateService.class);
        OutfitController controller = new OutfitController(outfitService, generator);
        UUID outfitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CustomUserDetails user = new CustomUserDetails(userId, "user@example.com", "USER");
        Outfit outfit = Outfit.builder().id(outfitId).build();
        byte[] bytes = {1, 2, 3};
        when(outfitService.getOwnedOutfit(outfitId, userId)).thenReturn(outfit);
        GeneratedOutfitImage generatedImage = new GeneratedOutfitImage(bytes, "image/png", "gemini-3.1-flash-image", "prompt");
        OutfitImageResponse expected = new OutfitImageResponse(outfitId, "https://storage.example/generated.png");
        when(generator.generateFitting(outfit)).thenReturn(generatedImage);
        when(outfitService.createImage(generatedImage, outfitId, userId)).thenReturn(expected);

        var response = controller.generate(outfitId, OutfitImageGenerationType.FITTING, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(outfitService).getOwnedOutfit(outfitId, userId);
        verify(generator).generateFitting(outfit);
        verify(outfitService).createImage(generatedImage, outfitId, userId);
    }

    @Test
    void generatesCompositionImageAndReturnsStoredImageUrl() {
        OutfitService outfitService = mock(OutfitService.class);
        OutfitImageGenerateService generator = mock(OutfitImageGenerateService.class);
        OutfitController controller = new OutfitController(outfitService, generator);
        UUID outfitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CustomUserDetails user = new CustomUserDetails(userId, "user@example.com", "USER");
        Outfit outfit = Outfit.builder().id(outfitId).build();
        byte[] bytes = {4, 5, 6};
        when(outfitService.getOwnedOutfit(outfitId, userId)).thenReturn(outfit);
        GeneratedOutfitImage generatedImage = new GeneratedOutfitImage(bytes, "image/webp", "gemini-3.1-flash-image", "prompt");
        OutfitImageResponse expected = new OutfitImageResponse(outfitId, "https://storage.example/generated.webp");
        when(generator.generateOverview(outfit)).thenReturn(generatedImage);
        when(outfitService.createImage(generatedImage, outfitId, userId)).thenReturn(expected);

        var response = controller.generate(outfitId, OutfitImageGenerationType.COMPOSITION, user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(generator).generateOverview(outfit);
        verify(outfitService).createImage(generatedImage, outfitId, userId);
    }

    @Test
    void storesUploadedOutfitImageAndReturnsPresignedUrl() {
        OutfitService outfitService = mock(OutfitService.class);
        OutfitImageGenerateService generator = mock(OutfitImageGenerateService.class);
        OutfitController controller = new OutfitController(outfitService, generator);
        UUID outfitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CustomUserDetails user = new CustomUserDetails(userId, "user@example.com", "USER");
        MockMultipartFile image = new MockMultipartFile("image", "outfit.png", "image/png", new byte[]{1, 2});
        OutfitImageResponse expected = new OutfitImageResponse(outfitId, "https://storage.example/original.png");
        when(outfitService.createImage(image, outfitId, userId)).thenReturn(expected);

        var response = controller.postImage(outfitId, image, user);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(expected);
        verify(outfitService).createImage(image, outfitId, userId);
    }

    @Test
    void deletesStoredOutfitImageAndClearsImageKey() {
        OutfitService outfitService = mock(OutfitService.class);
        OutfitImageGenerateService generator = mock(OutfitImageGenerateService.class);
        OutfitController controller = new OutfitController(outfitService, generator);
        UUID outfitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CustomUserDetails user = new CustomUserDetails(userId, "user@example.com", "USER");
        var response = controller.deleteImage(outfitId, user);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(outfitService).deleteImage(outfitId, userId);
    }
}
