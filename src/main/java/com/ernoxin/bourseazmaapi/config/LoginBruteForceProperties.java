package com.ernoxin.bourseazmaapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.login-brute-force")
public class LoginBruteForceProperties {

    private int maxAttempts = 5;
    private long lockoutDurationSeconds = 900;
    private long attemptWindowSeconds = 900;
    private String keyPrefix = "bourse:login-attempt";
}
