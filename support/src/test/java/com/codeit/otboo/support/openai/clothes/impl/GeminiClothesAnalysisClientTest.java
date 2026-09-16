package com.codeit.otboo.support.openai.clothes.impl;

import com.codeit.otboo.domain.clothes.enums.*;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisClientException;
import com.codeit.otboo.support.openai.jackson.DisplayNameEnumModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClothesAnalysisClientTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new DisplayNameEnumModule());

    @Test
    void sendsUrlContextAndSchemaAndResolvesOtherWithinCategory() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        String response = response(product(), "STOP", "URL_RETRIEVAL_STATUS_SUCCESS");
        GeminiClothesAnalysisClient client = client(request -> {
            assertThat(request.url().toString()).endsWith("/models/gemini-3.5-flash-lite:generateContent");
            assertThat(request.headers().getFirst("x-goog-api-key")).isEqualTo("test-key");
            MockClientHttpRequest output = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(output, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(output::getBodyAsString))
                    .flatMap(body -> {
                        try {
                            requestBody.set(mapper.readTree(body));
                            return Mono.just(ok(response));
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    });
        });

        var result = client.analyze("상품 URL: https://www.musinsa.com/products/123");

        assertThat(result.category()).isEqualTo(ClothesCategory.HAT);
        assertThat(result.subcategory()).isEqualTo(ClothesSubCategory.OTHER_HAT);
        assertThat(result.name()).isEqualTo("테스트 모자");
        assertThat(requestBody.get().at("/tools/0/url_context").isObject()).isTrue();
        assertThat(requestBody.get().at("/generationConfig/responseMimeType").asText())
                .isEqualTo("application/json");
        assertThat(requestBody.get().at("/generationConfig/responseJsonSchema/required").size()).isEqualTo(13);
    }

    @Test
    void rejectsFailedUrlRetrievalEvenWithValidProduct() throws Exception {
        assertRejected(response(product(), "STOP", "URL_RETRIEVAL_STATUS_ERROR"), "상품 URL 조회에 실패");
    }

    @Test
    void rejectsMissingRetrievalMetadata() throws Exception {
        ObjectNode response = (ObjectNode) mapper.readTree(response(product(), "STOP", "URL_RETRIEVAL_STATUS_SUCCESS"));
        ((ObjectNode) response.at("/candidates/0")).remove("urlContextMetadata");
        assertRejected(response.toString(), "상품 URL 조회 결과가 없습니다");
    }

    @Test
    void rejectsTruncatedResponse() throws Exception {
        assertRejected(response(product(), "MAX_TOKENS", "URL_RETRIEVAL_STATUS_SUCCESS"), "정상 완료되지 않았습니다");
    }

    @Test
    void rejectsSubcategoryFromAnotherCategory() throws Exception {
        ObjectNode product = product().put("subcategory", "숏패딩");
        assertRejected(response(product, "STOP", "URL_RETRIEVAL_STATUS_SUCCESS"), "잘못된 의류 속성: subcategory");
    }

    @Test
    void allowsUnverifiedImageUrlBecauseItIsResolvedSeparately() throws Exception {
        assertThat(client(request -> Mono.just(ok(response(product().put("imageUrl", ""), "STOP", "URL_RETRIEVAL_STATUS_SUCCESS"))))
                .analyze("상품 URL").imageUrl()).isEmpty();
        assertThat(client(request -> Mono.just(ok(response(product().put("imageUrl", "file:///image.jpg"), "STOP", "URL_RETRIEVAL_STATUS_SUCCESS"))))
                .analyze("상품 URL").imageUrl()).isEqualTo("file:///image.jpg");
    }

    @Test
    void ignoresThoughtPartsAndJoinsOutputParts() throws Exception {
        ObjectNode response = (ObjectNode) mapper.readTree(response(product(), "STOP", "URL_RETRIEVAL_STATUS_SUCCESS"));
        String json = product().toString();
        ((ObjectNode) response.at("/candidates/0/content")).set("parts", mapper.valueToTree(List.of(
                Map.of("thought", true, "text", "생각 중"),
                Map.of("text", json.substring(0, 30)), Map.of("text", json.substring(30)))));
        assertThat(client(request -> Mono.just(ok(response.toString()))).analyze("상품 URL").name())
                .isEqualTo("테스트 모자");
    }

    @Test
    void wrapsHttpErrorsWithoutRetrying() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build());
        });
        assertThatThrownBy(() -> client.analyze("상품 URL"))
                .isInstanceOf(ClothesAnalysisClientException.class)
                .hasCauseInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsMalformedJsonAndBlockedResponse() {
        assertThatThrownBy(() -> client(request -> Mono.just(ok("not json"))).analyze("상품 URL"))
                .isInstanceOf(ClothesAnalysisClientException.class);
        assertRejected("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", "정상 완료되지 않았습니다");
    }

    private void assertRejected(String response, String causeMessage) {
        assertThatThrownBy(() -> client(request -> Mono.just(ok(response))).analyze("상품 URL"))
                .isInstanceOf(ClothesAnalysisClientException.class)
                .rootCause().hasMessageContaining(causeMessage);
    }

    private GeminiClothesAnalysisClient client(ExchangeFunction exchange) {
        return new GeminiClothesAnalysisClient(WebClient.builder().exchangeFunction(exchange), mapper,
                "test-key", "gemini-3.5-flash-lite", Duration.ofSeconds(2), Duration.ZERO);
    }

    private ClientResponse ok(String body) {
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body).build();
    }

    private ObjectNode product() {
        ObjectNode data = mapper.createObjectNode();
        data.put("name", "테스트 모자").put("brand", "테스트 브랜드")
                .put("imageUrl", "https://images.example.com/hat.jpg").put("description", "면 소재의 모자.")
                .put("category", "모자").put("subcategory", "기타")
                .put("color", ClothesColor.values()[0].getDisplayName())
                .put("fit", "스탠다드").put("material", "면").put("pattern", "단색/무지")
                .put("style", "캐주얼").put("season", "사계절").put("gender", "혼성");
        return data;
    }

    private String response(ObjectNode product, String finishReason, String retrievalStatus) throws Exception {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "finishReason", finishReason,
                "content", Map.of("parts", List.of(Map.of("text", product.toString()))),
                "urlContextMetadata", Map.of("urlMetadata", List.of(Map.of(
                        "retrievedUrl", "https://www.musinsa.com/products/123",
                        "urlRetrievalStatus", retrievalStatus)))))));
    }
}
