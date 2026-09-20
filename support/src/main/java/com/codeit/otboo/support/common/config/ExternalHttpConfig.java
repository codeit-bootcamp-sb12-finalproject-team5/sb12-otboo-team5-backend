package com.codeit.otboo.support.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ExternalHttpConfig {

    @Bean
    public RestClient externalRestClient() {
        return RestClient.builder()
                .build();
    }

}
