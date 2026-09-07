package com.codeit.otboo.api.weather.client;

import com.codeit.otboo.api.weather.dto.response.KakaoRegionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class KakaoClient {
	private final String apiKey;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public Optional<KakaoRegionDto> findAdministrativeRegion(double longitude, double latitude) {
		if (apiKey.isBlank()) {
			log.warn("[KAKAO] REST API 키가 없어 행정동 조회를 건너뜁니다.");
			return Optional.empty();
		}

		try {
			log.info("[KAKAO] 행정동 조회 시작 longitude={}, latitude={}", longitude, latitude);
			String body = restClient.get()
				.uri(uri -> uri.path("/v2/local/geo/coord2regioncode.json")
					.queryParam("x", longitude)
					.queryParam("y", latitude)
					.queryParam("input_coord", "WGS84")
					.build())
				.retrieve()
				.body(String.class);

			JsonNode documents = objectMapper.readTree(body).path("documents");
			JsonNode region = null;
			for (JsonNode document : documents) {
				if ("H".equals(document.path("region_type").asText())) {
					region = document;
					break;
				}
			}
			if (region == null) {
				log.warn("[KAKAO] 응답 {}건 중 행정동(H) 문서를 찾지 못했습니다.", documents.size());
				return Optional.empty();
			}

			KakaoRegionDto selected = new KakaoRegionDto(
				region.path("region_type").asText(),
				region.path("code").asText(),
				region.path("address_name").asText(),
				region.path("region_1depth_name").asText(),
				region.path("region_2depth_name").asText(),
				region.path("region_3depth_name").asText(),
				region.path("region_4depth_name").asText(),
				region.path("x").asDouble(),
				region.path("y").asDouble());
			log.info("[KAKAO] 행정동 선택 type={}, code={}, address={}",
				selected.regionType(), selected.code(), selected.addressName());
			return Optional.of(selected);
		} catch (Exception exception) {
			log.warn("[KAKAO] 행정동 조회 실패: {}", exception.getMessage());
			return Optional.empty();
		}
	}
}