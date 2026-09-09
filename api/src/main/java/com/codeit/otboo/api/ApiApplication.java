package com.codeit.otboo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.codeit.otboo")
@ConfigurationPropertiesScan
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@EntityScan(basePackages = "com.codeit.otboo.domain")
@EnableJpaRepositories(basePackages = "com.codeit.otboo.domain")
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
