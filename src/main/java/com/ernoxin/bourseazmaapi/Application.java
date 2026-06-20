package com.ernoxin.bourseazmaapi;

import com.ernoxin.bourseazmaapi.config.JwtProperties;
import com.ernoxin.bourseazmaapi.config.LoginBruteForceProperties;
import com.ernoxin.bourseazmaapi.config.OrderMatchingProperties;
import com.ernoxin.bourseazmaapi.config.TsetmcApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        JwtProperties.class,
        LoginBruteForceProperties.class,
        TsetmcApiProperties.class,
        OrderMatchingProperties.class
})
@PropertySource(value = "file://${CONFIG}/properties/bourse-azma-api.properties", ignoreResourceNotFound = true)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
