package com.codeit.otboo.support.llm.outfit;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.support.storage.S3StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutfitImageGenerateServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OutfitClothesRepository repository = mock(OutfitClothesRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();

    @Test
    void sendsEveryOutfitImageAndReturnsGeneratedImage() throws Exception {
        Clothes top = clothing("top.png", ClothesCategory.TOP);
        Clothes pants = clothing("pants.png", ClothesCategory.PANTS);
        when(repository.findClothesByOutfitId(outfit.getId())).thenReturn(List.of(top, pants));
        when(storage.getObject("top.png"))
                .thenReturn(new S3StorageService.StoredObject("top.png".getBytes(), "image/png"));
        when(storage.getObject("pants.png"))
                .thenReturn(new S3StorageService.StoredObject("pants.png".getBytes(), "image/png"));
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url().toString()).endsWith("/models/gemini-3.1-flash-image:generateContent");
            assertThat(request.headers().getFirst("x-goog-api-key")).isEqualTo("test-key");
            MockClientHttpRequest output = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(output, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(output::getBodyAsString))
                    .flatMap(body -> {
                        try {
                            sent.set(mapper.readTree(body));
                            return Mono.just(ClientResponse.create(HttpStatus.OK).body("""
                                    {"candidates":[{"finishReason":"STOP","content":{"parts":[
                                      {"text":"done"},
                                      {"inlineData":{"mimeType":"image/png","data":"AQID"}}
                                    ]}}]}
                                    """).build());
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    });
        });

        var result = service(builder).generateFitting(outfit);

        assertThat(result.imageBytes()).containsExactly(1, 2, 3);
        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.model()).isEqualTo("gemini-3.1-flash-image");
        assertThat(sent.get().at("/contents/0/parts").size()).isEqualTo(3);
        assertThat(sent.get().at("/contents/0/parts/0/text").asText()).contains("TOP", "PANTS");
        assertThat(sent.get().at("/contents/0/parts/0/text").asText())
                .contains("without a specified gender", "gender: BOTH");
        assertThat(sent.get().at("/contents/0/parts/1/inline_data/data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString("top.png".getBytes()));
        assertThat(sent.get().at("/contents/0/parts/2/inline_data/data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString("pants.png".getBytes()));
        assertThat(sent.get().at("/generationConfig/responseModalities/0").asText()).isEqualTo("IMAGE");
        assertThat(sent.get().at("/generationConfig/imageConfig/aspectRatio").asText()).isEqualTo("2:3");
        assertThat(sent.get().at("/generationConfig/imageConfig/imageSize").asText()).isEqualTo("1K");
        assertThat(sent.get().at("/generationConfig/responseFormat").isMissingNode()).isTrue();
        verify(storage).getObject("top.png");
        verify(storage).getObject("pants.png");
    }

    @Test
    void choosesModelPresentationFromOutfitClothingGenders() {
        String malePrompt = generatedPromptFor(List.of(
                clothing("male.png", ClothesCategory.TOP, ClothesGender.MALE),
                clothing("unisex.png", ClothesCategory.PANTS, ClothesGender.BOTH)));
        assertThat(malePrompt).contains("Use an adult male-presenting model.", "gender: MALE", "gender: BOTH");

        String femalePrompt = generatedPromptFor(List.of(
                clothing("female.png", ClothesCategory.TOP, ClothesGender.FEMALE)));
        assertThat(femalePrompt).contains("Use an adult female-presenting model.", "gender: FEMALE");

        String mixedPrompt = generatedPromptFor(List.of(
                clothing("male.png", ClothesCategory.TOP, ClothesGender.MALE),
                clothing("female.png", ClothesCategory.PANTS, ClothesGender.FEMALE)));
        assertThat(mixedPrompt).contains("Use an adult model without a specified gender.");
    }

    @Test
    void createsSquareIsolatedCompositionForEveryOutfitImage() throws Exception {
        Clothes top = clothing("top.png", ClothesCategory.TOP);
        Clothes shoes = clothing("shoes.png", ClothesCategory.SHOES);
        when(repository.findClothesByOutfitId(outfit.getId())).thenReturn(List.of(top, shoes));
        when(storage.getObject("top.png"))
                .thenReturn(new S3StorageService.StoredObject("top.png".getBytes(), "image/png"));
        when(storage.getObject("shoes.png"))
                .thenReturn(new S3StorageService.StoredObject("shoes.png".getBytes(), "image/png"));
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        WebClient.Builder builder = capturingBuilder(sent);

        var result = service(builder).generateOverview(outfit);

        assertThat(result.prompt()).contains("isolated product cutout", "No people", "TOP", "SHOES");
        assertThat(sent.get().at("/contents/0/parts").size()).isEqualTo(3);
        assertThat(sent.get().at("/generationConfig/imageConfig/aspectRatio").asText()).isEqualTo("1:1");
    }

    @Test
    void rejectsOutfitWhenAnItemHasNoImage() {
        Clothes top = clothing(null, ClothesCategory.TOP);
        when(repository.findClothesByOutfitId(outfit.getId())).thenReturn(List.of(top));

        assertThatThrownBy(() -> service(WebClient.builder()).generateFitting(outfit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing image");
    }

    @Test
    void includesGeminiErrorMessageWhenRequestIsRejected() {
        Clothes top = clothing("top.png", ClothesCategory.TOP);
        when(repository.findClothesByOutfitId(outfit.getId())).thenReturn(List.of(top));
        when(storage.getObject("top.png"))
                .thenReturn(new S3StorageService.StoredObject("image".getBytes(), "image/png"));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"message\":\"Invalid image payload\"}}")
                        .build()));

        assertThatThrownBy(() -> service(builder).generateFitting(outfit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid image payload");
    }

    private Clothes clothing(String imageKey, ClothesCategory category) {
        return clothing(imageKey, category, ClothesGender.BOTH);
    }

    private Clothes clothing(String imageKey, ClothesCategory category, ClothesGender gender) {
        return Clothes.builder().id(UUID.randomUUID()).name(category.name())
                .category(category).gender(gender).imageUrl(imageKey).build();
    }

    private String generatedPromptFor(List<Clothes> clothes) {
        when(repository.findClothesByOutfitId(outfit.getId())).thenReturn(clothes);
        for (Clothes item : clothes) {
            when(storage.getObject(item.getImageUrl()))
                    .thenReturn(new S3StorageService.StoredObject("image".getBytes(), "image/png"));
        }
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .body("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"AQID\"}}]}}]}")
                        .build()));
        return service(builder).generateFitting(outfit).prompt();
    }

    private WebClient.Builder capturingBuilder(AtomicReference<JsonNode> sent) {
        return WebClient.builder().exchangeFunction(request -> {
            MockClientHttpRequest output = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(output, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(output::getBodyAsString))
                    .flatMap(body -> {
                        try {
                            sent.set(mapper.readTree(body));
                            return Mono.just(ClientResponse.create(HttpStatus.OK)
                                    .body("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"AQID\"}}]}}]}")
                                    .build());
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    });
        });
    }

    private OutfitImageGenerateService service(WebClient.Builder builder) {
        return new OutfitImageGenerateService(repository, storage, builder, mapper,
                "test-key", "gemini-3.1-flash-image", Duration.ofSeconds(2));
    }
}
