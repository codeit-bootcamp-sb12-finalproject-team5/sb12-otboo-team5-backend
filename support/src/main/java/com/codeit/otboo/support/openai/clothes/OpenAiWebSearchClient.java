package com.codeit.otboo.support.openai.clothes;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class OpenAiWebSearchClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    public OpenAiWebSearchClient(
        WebClient.Builder builder,
        ObjectMapper objectMapper
    ) {
        this.webClient = builder
            .baseUrl("https://api.openai.com/v1")
            .build();

        this.objectMapper = objectMapper;
    }

    public ClothesAnalysisResult analyze(String prompt) {
        long start = System.currentTimeMillis();

        String response = webClient.post()
            .uri("/responses")
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + apiKey
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest(prompt))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        long openAiEnd = System.currentTimeMillis();

        try {
            JsonNode root = objectMapper.readTree(response);

            String result = extractOutputText(root);

            ClothesAnalysisResult analysis = objectMapper.readValue(
                result,
                ClothesAnalysisResult.class
            );

            long end = System.currentTimeMillis();
            log.info(
                "================ OpenAI API = {} ms, JSON parsing = {} ms, total = {} ms",
                openAiEnd - start,
                end - openAiEnd,
                end - start
            );
            log.info(
                "================ OpenAI Result = {}",
                analysis
            );

            return analysis;

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "OpenAI 의류 분석 결과 파싱에 실패했습니다.",
                e
            );
        }
    }
    private Map<String, Object> createRequest(String prompt) {

        Map<String, Object> webSearchTool = Map.of(
            "type", "web_search",
            "search_context_size", "low",
            "filters", Map.of(
                "allowed_domains", List.of(
                    "musinsa.com"
                )
            )
        );

        return Map.of(
            "model", "gpt-5-mini",

            "tools", List.of(
                webSearchTool
            ),

            "input", prompt,

            "text", Map.of(
                "format", Map.of(
                    "type", "json_schema",
                    "name", "clothes_analysis",
                    "strict", true,
                    "schema", createSchema()
                )
            )
        );
    }
    private Map<String, Object> createSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put(
            "name",
            Map.of("type", "string")
        );

        properties.put(
            "imageUrl",
            Map.of("type", "string")
        );

        properties.put(
            "gender",
            enumSchema(ClothesGender.values())
        );

        properties.put(
            "category",
            enumSchema(ClothesCategory.values())
        );

        properties.put(
            "subcategory",
            enumSchema(ClothesSubCategory.values())
        );

        properties.put(
            "color",
            enumSchema(ClothesColor.values())
        );

        properties.put(
            "fit",
            enumSchema(ClothesFit.values())
        );

        properties.put(
            "material",
            enumSchema(ClothesMaterial.values())
        );

        properties.put(
            "pattern",
            enumSchema(ClothesPattern.values())
        );

        properties.put(
            "style",
            enumSchema(ClothesStyle.values())
        );

        properties.put(
            "season",
            enumSchema(ClothesSeason.values())
        );

        properties.put(
            "brand",
            Map.of(
                "type", "string"
            )
        );

        return Map.of(
            "type", "object",

            "properties", properties,

            "required", List.of(
                "name",
                "imageUrl",
                "gender",
                "category",
                "subcategory",
                "color",
                "fit",
                "material",
                "pattern",
                "style",
                "season",
                "brand"
            ),

            "additionalProperties", false
        );
    }
    private Map<String, Object> enumSchema(
        Displayable[] values
    ) {
        return Map.of(
            "type", "string",
            "enum", Arrays.stream(values)
                .map(Displayable::getDisplayName)
                .toList()
        );
    }
    private String extractOutputText(JsonNode root) {

        for (JsonNode output : root.path("output")) {

            if (!"message".equals(output.path("type").asText())) {
                continue;
            }

            for (JsonNode content : output.path("content")) {

                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
            }
        }

        throw new IllegalStateException(
            "OpenAI 응답에서 분석 결과를 찾을 수 없습니다."
        );
    }
}
