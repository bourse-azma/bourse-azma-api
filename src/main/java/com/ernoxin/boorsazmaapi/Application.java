package com.ernoxin.boorsazmaapi;

import com.ernoxin.boorsazmaapi.config.JwtProperties;
import com.ernoxin.boorsazmaapi.config.OrderMatchingProperties;
import com.ernoxin.boorsazmaapi.config.TsetmcApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, TsetmcApiProperties.class, OrderMatchingProperties.class})
@PropertySource(value = "file://${CONFIG}/properties/boors-azma-api.properties", ignoreResourceNotFound = true)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
