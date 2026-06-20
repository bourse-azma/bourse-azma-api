package com.ernoxin.bourseazmaapi.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    RestTemplate tsetmcRestTemplate(RestTemplateBuilder builder, TsetmcApiProperties properties) {
        return builder
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.readTimeoutMs()))
                .build();
    }
}
