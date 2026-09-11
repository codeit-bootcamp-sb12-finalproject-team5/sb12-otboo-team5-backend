package com.codeit.otboo.support.weather.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.codeit.otboo.support.weather.client.KmaClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class KmaClientConfig {

    private static final Duration EXTERNAL_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration EXTERNAL_READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public KmaClient kmaClient(
        @Value("${kma.service-key:}") String serviceKey,
        ObjectMapper objectMapper
    ) {
        RestClient restClient = RestClient.builder()
            .baseUrl("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0")
            .requestFactory(requestFactory(EXTERNAL_READ_TIMEOUT))
            .build();

        return new KmaClient(serviceKey, restClient, objectMapper);
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
