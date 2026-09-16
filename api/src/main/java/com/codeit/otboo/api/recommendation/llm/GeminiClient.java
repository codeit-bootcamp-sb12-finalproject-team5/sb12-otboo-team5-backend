package com.codeit.otboo.api.recommendation.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiClient {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiClient(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        @Value("${gemini.api-key}") String apiKey,
        @Value("${gemini.model}") String model
    ) {
        this.webClient = webClientBuilder
            .baseUrl("https://generativelanguage.googleapis.com")
            .defaultHeader("x-goog-api-key", apiKey)
            .build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public GeminiRecommendationResult generate(String prompt, LlmRecommendationRequest request) {
        String context = serialize(request);

        JsonNode rawResponse = webClient.post()
            .uri("/v1beta/models/{model}:generateContent", model)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", prompt))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", context)))),
                "generationConfig", Map.of(
                    "responseMimeType", MediaType.APPLICATION_JSON_VALUE,
                    "responseJsonSchema", responseSchema()
                )
            ))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        if (rawResponse == null) {
            throw new IllegalStateException("Gemini returned an empty response");
        }

        String responseText = rawResponse.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        if (responseText.isBlank()) {
            throw new IllegalStateException("Gemini response does not contain structured output text");
        }

        try {
            LlmRecommendationResponse recommendation = objectMapper.readValue(responseText, LlmRecommendationResponse.class);
            JsonNode usage = rawResponse.path("usageMetadata");

            return new GeminiRecommendationResult(
                recommendation,
                rawResponse.path("modelVersion").isMissingNode() ? null : rawResponse.path("modelVersion").asText(null),
                new GeminiRecommendationResult.Usage(
                    optionalInt(usage, "promptTokenCount"),
                    optionalInt(usage, "candidatesTokenCount"),
                    optionalInt(usage, "totalTokenCount")));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse Gemini structured output", exception);
        }
    }

    private String serialize(LlmRecommendationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Gemini recommendation context", exception);
        }
    }

    private Integer optionalInt(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> outfit = new LinkedHashMap<>();
        outfit.put("type", "object");
        outfit.put("properties", Map.of(
            "rank", Map.of("type", "integer"),
            "clothesIds", Map.of("type", "array", "items", Map.of("type", "string")),
            "reason", Map.of("type", "string"),
            "styleTags", Map.of("type", "array", "items", Map.of("type", "string"))));
        outfit.put("required", List.of("rank", "clothesIds", "reason", "styleTags"));

        return Map.of(
            "type", "object",
            "properties", Map.of("outfits", Map.of("type", "array", "maxItems", 3, "items", outfit)),
            "required", List.of("outfits"));
    }
}
