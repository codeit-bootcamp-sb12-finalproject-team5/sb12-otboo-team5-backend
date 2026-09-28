package com.codeit.otboo.support.llm.config;

import com.codeit.otboo.support.llm.jackson.DisplayNameEnumModule;
import com.fasterxml.jackson.databind.Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public Module displayNameEnumModule() {
        return new DisplayNameEnumModule();
    }
}
