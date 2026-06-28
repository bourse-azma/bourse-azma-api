package com.ernoxin.bourseazmaapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins();
        if (CollectionUtils.isEmpty(allowedOrigins)) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins is not configured. Set it in application.properties or environment."
            );
        }

        List<String> allowedMethods = corsProperties.getAllowedMethods();
        if (CollectionUtils.isEmpty(allowedMethods)) {
            throw new IllegalStateException(
                    "app.cors.allowed-methods is not configured. Set it in application.properties or environment."
            );
        }

        List<String> allowedHeaders = corsProperties.getAllowedHeaders();
        if (CollectionUtils.isEmpty(allowedHeaders)) {
            throw new IllegalStateException(
                    "app.cors.allowed-headers is not configured. Set it in application.properties or environment."
            );
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
