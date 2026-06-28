package com.ernoxin.bourseazmaapi.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    RestTemplate tsetmcRestTemplate(RestTemplateBuilder builder, TsetmcApiProperties properties) {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(
                        PoolingHttpClientConnectionManagerBuilder.create()
                                .setMaxConnTotal(50)
                                .setMaxConnPerRoute(50)
                                .build()
                )
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return builder
                .requestFactory(() -> factory)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.readTimeoutMs()))
                .build();
    }
}
