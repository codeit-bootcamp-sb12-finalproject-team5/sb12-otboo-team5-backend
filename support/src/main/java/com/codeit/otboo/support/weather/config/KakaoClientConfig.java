package com.codeit.otboo.support.weather.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.codeit.otboo.support.weather.client.KakaoClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class KakaoClientConfig {

    private static final Duration EXTERNAL_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration EXTERNAL_READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public KakaoClient kakaoClient(
        @Value("${kakao.rest-api-key:}") String apiKey,
        ObjectMapper objectMapper
    ) {
        RestClient restClient = RestClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .defaultHeader("Authorization", "KakaoAK " + apiKey)
            .requestFactory(requestFactory(EXTERNAL_READ_TIMEOUT))
            .build();

        return new KakaoClient(apiKey, restClient, objectMapper);
    }

    private ClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(EXTERNAL_CONNECT_TIMEOUT)
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
