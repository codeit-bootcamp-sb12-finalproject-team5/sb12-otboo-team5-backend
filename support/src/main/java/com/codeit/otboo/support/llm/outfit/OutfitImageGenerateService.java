package com.codeit.otboo.support.llm.outfit;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.support.llm.outfit.dto.GeneratedOutfitImage;
import com.codeit.otboo.support.storage.S3StorageService;
import com.codeit.otboo.support.storage.ImageContentType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@Slf4j
public class OutfitImageGenerateService {

    private static final int MAX_REFERENCE_IMAGES = 10;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_INLINE_REQUEST_BYTES = 18L * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 20 * 1024 * 1024;

    private final OutfitClothesRepository outfitClothesRepository;
    private final S3StorageService s3StorageService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public OutfitImageGenerateService(
            OutfitClothesRepository outfitClothesRepository,
            S3StorageService s3StorageService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.outfit-look.model:gemini-3.1-flash-image}") String model,
            @Value("${gemini.outfit-look.timeout:120s}") Duration timeout
    ) {
        this.outfitClothesRepository = outfitClothesRepository;
        this.s3StorageService = s3StorageService;
        this.webClient = webClientBuilder.clone()
                .baseUrl("https://generativelanguage.googleapis.com/v1")
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    /** Generates a styled full-body look on a generic model from the outfit's clothing images. */
    public GeneratedOutfitImage generateFitting(Outfit outfit) {
        return generate(outfit, this::buildFittingPrompt, "2:3");
    }

    /** Generates an isolated clothing composition board from the outfit's clothing images. */
    public GeneratedOutfitImage generateOverview(Outfit outfit) {
        return generate(outfit, this::buildCompositionPrompt, "1:1");
    }

    private GeneratedOutfitImage generate(
            Outfit outfit,
            Function<List<Clothes>, String> promptBuilder,
            String aspectRatio
    ) {
        Objects.requireNonNull(outfit, "outfit is required");
        if (outfit.getId() == null || apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("A saved outfit and Gemini API key are required");
        }

        List<Clothes> clothes = outfitClothesRepository.findClothesByOutfitId(outfit.getId());
        if (clothes.isEmpty() || clothes.size() > MAX_REFERENCE_IMAGES) {
            throw new IllegalArgumentException("An outfit needs 1 to 10 clothing images");
        }

        String prompt = promptBuilder.apply(clothes);
        long requestBytes = prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        for (Clothes item : clothes) {
            String imageKey = item.getImageUrl();
            if (imageKey == null || imageKey.isBlank()) {
                throw new IllegalArgumentException("Missing image for clothing item " + item.getId());
            }
            S3StorageService.StoredObject image = s3StorageService.getObject(imageKey);
            byte[] bytes = image.bytes();
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Clothing image must be between 1 byte and 10 MB: " + item.getId());
            }
            String mimeType;
            try {
                mimeType = ImageContentType.resolve(bytes, image.contentType());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot identify clothing image type: "
                        + item.getId() + " (key=" + imageKey + ", contentType=" + image.contentType() + ")", e);
            }
            if (!List.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
                    .contains(mimeType)) {
                throw new IllegalArgumentException("Unsupported clothing image type: " + mimeType
                        + " (clothesId=" + item.getId() + ", key=" + imageKey + ")");
            }
            requestBytes += ((long) bytes.length + 2) / 3 * 4;
            if (requestBytes > MAX_INLINE_REQUEST_BYTES) {
                throw new IllegalArgumentException("Combined clothing images exceed the Gemini inline request size limit");
            }
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", mimeType,
                    "data", Base64.getEncoder().encodeToString(bytes))));
        }

        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of(
                        "responseModalities", List.of("IMAGE"),
                        "imageConfig", Map.of("aspectRatio", aspectRatio, "imageSize", "1K")));
        String response;
        try {
            response = webClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(timeout);
        } catch (WebClientResponseException e) {
            String message = extractGeminiError(e);
            log.warn("Gemini image generation failed: status={}, model={}, message={}",
                    e.getStatusCode().value(), model, message);
            throw new IllegalStateException("Gemini image generation failed: " + message, e);
        }
        return parseResponse(response, prompt);
    }
    private String extractGeminiError(WebClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            if (body != null) {
                String message = body.path("error").path("message").asText();
                if (!message.isBlank()) {
                    return message.length() > 500 ? message.substring(0, 500) : message;
                }
            }
        } catch (JsonProcessingException ignored) {
            // The response may not contain a JSON error body.
        }
        return exception.getStatusText();
    }
    private String buildFittingPrompt(List<Clothes> clothes) {
        boolean hasMaleClothes = clothes.stream().anyMatch(item -> item.getGender() == ClothesGender.MALE);
        boolean hasFemaleClothes = clothes.stream().anyMatch(item -> item.getGender() == ClothesGender.FEMALE);
        String modelGuidance = hasMaleClothes && !hasFemaleClothes
                ? "Use an adult male-presenting model."
                : hasFemaleClothes && !hasMaleClothes
                ? "Use an adult female-presenting model."
                : "Use an adult model without a specified gender.";
        StringBuilder prompt = new StringBuilder("""
                Create one photorealistic, full-body fashion look photo of an adult model wearing ALL the supplied clothing items together.
                Each reference image corresponds to the numbered item below in the same order. Preserve each item's visible color, pattern, cut, and design as closely as possible.
                Place outerwear over tops, bottoms below tops, shoes on feet, and accessories in their natural position.
                Use a neutral studio background, natural standing pose, and show the entire outfit from head to toe. No collage, labels, text, or extra garments.
                This is a styling visualization; do not imply an exact physical fit or the identity of a real person.
                """);
        prompt.append(modelGuidance).append('\n');
        for (int i = 0; i < clothes.size(); i++) {
            Clothes item = clothes.get(i);
            prompt.append(i + 1).append(". ").append(item.getCategory().name())
                    .append(": ").append(item.getName())
                    .append(" (gender: ").append(item.getGender().name()).append(")\n");
        }
        return prompt.toString();
    }
    private String buildCompositionPrompt(List<Clothes> clothes) {
        StringBuilder prompt = new StringBuilder("""
                Create one clean, square fashion outfit composition board using ALL supplied clothing items.
                Remove the original backgrounds and present every supplied item as a separate, isolated product cutout on a plain warm-white studio background.
                Arrange the items into a balanced editorial flat-lay composition that resembles a curated outfit board. Keep every item fully visible, recognizable, and separate; do not merge them into one garment or place them on a person, mannequin, or model.
                Preserve each item's visible color, pattern, material, cut, and design as closely as possible. Use natural relative sizing and a small amount of clean spacing between items.
                No people, body parts, text, labels, logos, watermark, collage frames, extra garments, or decorative objects.
                Each reference image corresponds to the numbered item below in the same order.
                """);
        appendClothes(prompt, clothes);
        return prompt.toString();
    }
    private void appendClothes(StringBuilder prompt, List<Clothes> clothes) {
        for (int i = 0; i < clothes.size(); i++) {
            Clothes item = clothes.get(i);
            prompt.append(i + 1).append(". ").append(item.getCategory().name())
                    .append(": ").append(item.getName())
                    .append(" (gender: ").append(item.getGender().name()).append(")\n");
        }
    }
    private GeneratedOutfitImage parseResponse(String response, String prompt) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response");
        }
        try {
            JsonNode candidate = objectMapper.readTree(response).path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) {
                throw new IllegalStateException("Gemini image generation ended with: "
                        + candidate.path("finishReason").asText("NO_CANDIDATE"));
            }
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                JsonNode inlineData = part.path("inlineData");
                String mimeType = inlineData.path("mimeType").asText();
                String data = inlineData.path("data").asText();
                if (mimeType.startsWith("image/") && !data.isBlank()) {
                    return new GeneratedOutfitImage(Base64.getDecoder().decode(data), mimeType, model, prompt);
                }
            }
            throw new IllegalStateException("Gemini did not return a generated image");
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Gemini image response", e);
        }
    }


}
