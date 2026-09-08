package com.codeit.otboo.support.weather.client;

import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaForecastPointDto;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;

import static com.codeit.otboo.support.weather.util.KmaTimeCalculator.DATE_FORMATTER;
import static com.codeit.otboo.support.weather.util.KmaTimeCalculator.TIME_FORMATTER;
import static com.codeit.otboo.support.weather.util.KmaTimeCalculator.DATE_TIME_FORMATTER;
import static com.codeit.otboo.support.weather.util.KmaTimeCalculator.normalizeToKstHour;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class KmaClient {
    private final String serviceKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public Optional<KmaObservationDto> findLatestObservation(int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();

        OffsetDateTime candidate = KmaTimeCalculator.currentObservationBase();
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<KmaObservationDto> result = requestObservation(candidate.minusHours(attempt), nx, ny);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    public Optional<KmaObservationDto> findObservation(OffsetDateTime observedAt, int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();
        return requestObservation(normalizeToKstHour(observedAt), nx, ny);
    }

    public Optional<KmaForecastBundleDto> findLatestVillageForecast(int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();

        OffsetDateTime candidate = KmaTimeCalculator.currentVillageBase();
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<KmaForecastBundleDto> result = requestVillageForecast(
                    candidate.minusHours(attempt * 3L), nx, ny);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    public Optional<KmaForecastBundleDto> findVillageForecast(
            OffsetDateTime forecastedAt, int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();
        return requestVillageForecast(
                normalizeToKstHour(forecastedAt), nx, ny);
    }

    private Optional<KmaForecastBundleDto> requestVillageForecast(OffsetDateTime base, int nx, int ny) {
        try {
            log.info("[KMA] 단기예보 요청 baseDate={}, baseTime={}, nx={}, ny={}",
                    base.format(DATE_FORMATTER), base.format(TIME_FORMATTER), nx, ny);
            String body = restClient.get()
                    .uri(uri -> uri.path("/getVilageFcst")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 1100)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", base.format(DATE_FORMATTER))
                            .queryParam("base_time", base.format(TIME_FORMATTER))
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!isSuccessful(root)) return Optional.empty();

            JsonNode items = root.path("response").path("body").path("items").path("item");
            if (!items.isArray() || items.isEmpty()) return Optional.empty();

            List<KmaForecastPointDto> points = new ArrayList<>();
            for (JsonNode item : items) {
                OffsetDateTime forecastAt = OffsetDateTime.parse(
                        item.path("fcstDate").asText() + item.path("fcstTime").asText(), DATE_TIME_FORMATTER);
                points.add(new KmaForecastPointDto(forecastAt,
                        item.path("category").asText(), item.path("fcstValue").asText()));
            }
            log.info("[KMA] 단기예보 응답 성공 base={}, itemCount={}", base, points.size());
            return Optional.of(new KmaForecastBundleDto(base, nx, ny, points));
        } catch (Exception exception) {
            log.warn("[KMA] 단기예보 요청 실패 base={}, nx={}, ny={}, exception={}",
                    base, nx, ny, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<KmaObservationDto> requestObservation(OffsetDateTime base, int nx, int ny) {
        try {
            log.info("[KMA] 초단기실황 요청 baseDate={}, baseTime={}, nx={}, ny={}",
                    base.format(DATE_FORMATTER), base.format(TIME_FORMATTER), nx, ny);
            String body = restClient.get()
                    .uri(uri -> uri.path("/getUltraSrtNcst")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 100)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", base.format(DATE_FORMATTER))
                            .queryParam("base_time", base.format(TIME_FORMATTER))
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!isSuccessful(root)) return Optional.empty();

            JsonNode items = root.path("response").path("body").path("items").path("item");
            if (!items.isArray() || items.isEmpty()) return Optional.empty();

            Map<String, String> values = new LinkedHashMap<>();
            for (JsonNode item : items) {
                values.put(item.path("category").asText(), item.path("obsrValue").asText());
            }
            OffsetDateTime observedAt = OffsetDateTime.parse(
                    items.get(0).path("baseDate").asText() + items.get(0).path("baseTime").asText(), DATE_TIME_FORMATTER);
            log.info("[KMA] 초단기실황 응답 성공 observedAt={}, categoryCount={}",
                    observedAt, values.size());
            return Optional.of(new KmaObservationDto(observedAt, nx, ny, values));
        } catch (Exception exception) {
            log.warn("[KMA] 초단기실황 요청 실패 base={}, nx={}, ny={}, exception={}",
                    base, nx, ny, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean isSuccessful(JsonNode root) {
        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asText();
        boolean successful = "00".equals(resultCode);
        if (!successful) {
            log.warn("[KMA] API 오류 응답 resultCode={}, resultMessage={}",
                    resultCode, header.path("resultMsg").asText());
        }
        return successful;
    }
}
