package com.codeit.otboo.support.weather.client;

import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaForecastPointDto;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class KmaClient {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final String serviceKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public Optional<KmaObservationDto> findLatestObservation(int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();

        LocalDateTime candidate = LocalDateTime.now(KST).minusMinutes(15)
                .withMinute(0).withSecond(0).withNano(0);
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<KmaObservationDto> result = requestObservation(candidate.minusHours(attempt), nx, ny);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    public Optional<KmaObservationDto> findObservation(LocalDateTime observedAt, int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();
        return requestObservation(observedAt.withMinute(0).withSecond(0).withNano(0), nx, ny);
    }

    public Optional<KmaForecastBundleDto> findLatestVillageForecast(int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();

        LocalDateTime candidate = latestVillageBase(LocalDateTime.now(KST).minusMinutes(15));
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<KmaForecastBundleDto> result = requestVillageForecast(
                    candidate.minusHours(attempt * 3L), nx, ny);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    /** 지정한 발표 시각과 격자의 단기예보를 조회합니다. */
    public Optional<KmaForecastBundleDto> findVillageForecast(
            LocalDateTime forecastedAt, int nx, int ny) {
        if (serviceKey.isBlank()) return Optional.empty();
        return requestVillageForecast(
                forecastedAt.withMinute(0).withSecond(0).withNano(0), nx, ny);
    }

    /** 기준 시각보다 15분 이상 지난 가장 가까운 단기예보 발표 시각을 계산합니다. */
    private LocalDateTime latestVillageBase(LocalDateTime time) {
        int[] hours = {2, 5, 8, 11, 14, 17, 20, 23};
        for (int index = hours.length - 1; index >= 0; index--) {
            if (time.getHour() >= hours[index]) {
                return time.withHour(hours[index]).withMinute(0).withSecond(0).withNano(0);
            }
        }
        return time.minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0);
    }

    /** 기상청 단기예보 엔드포인트를 호출하고 발표본 단위 DTO로 변환합니다. */
    private Optional<KmaForecastBundleDto> requestVillageForecast(LocalDateTime base, int nx, int ny) {
        try {
            log.info("[KMA] 단기예보 요청 baseDate={}, baseTime={}, nx={}, ny={}",
                    base.format(DATE), base.format(TIME), nx, ny);
            String body = restClient.get()
                    .uri(uri -> uri.path("/getVilageFcst")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 1100)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", base.format(DATE))
                            .queryParam("base_time", base.format(TIME))
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
                LocalDateTime forecastAt = LocalDateTime.parse(
                        item.path("fcstDate").asText() + item.path("fcstTime").asText(), DATE_TIME);
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

    /** 기상청 초단기실황 엔드포인트를 호출하고 항목별 값 DTO로 변환합니다. */
    private Optional<KmaObservationDto> requestObservation(LocalDateTime base, int nx, int ny) {
        try {
            log.info("[KMA] 초단기실황 요청 baseDate={}, baseTime={}, nx={}, ny={}",
                    base.format(DATE), base.format(TIME), nx, ny);
            String body = restClient.get()
                    .uri(uri -> uri.path("/getUltraSrtNcst")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 100)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", base.format(DATE))
                            .queryParam("base_time", base.format(TIME))
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
            LocalDateTime observedAt = LocalDateTime.parse(
                    items.get(0).path("baseDate").asText() + items.get(0).path("baseTime").asText(), DATE_TIME);
            log.info("[KMA] 초단기실황 응답 성공 observedAt={}, categoryCount={}",
                    observedAt, values.size());
            return Optional.of(new KmaObservationDto(observedAt, nx, ny, values));
        } catch (Exception exception) {
            log.warn("[KMA] 초단기실황 요청 실패 base={}, nx={}, ny={}, exception={}",
                    base, nx, ny, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 기상청 공통 응답 헤더의 정상 처리 코드를 확인합니다. */
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
