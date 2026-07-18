package com.ernoxin.bourseazmaapi.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    @NotBlank
    @Pattern(regexp = "^/[A-Za-z0-9/_-]*[A-Za-z0-9_-]$", message = "must be an absolute path without a trailing slash")
    private String endpoint = "/ws-api";

    @Valid
    private Heartbeat heartbeat = new Heartbeat();

    @Valid
    private RedisFanout redisFanout = new RedisFanout();

    @Getter
    @Setter
    public static class Heartbeat {

        @Min(1_000)
        private long serverToClientMs = 10_000;

        @Min(1_000)
        private long clientToServerMs = 10_000;
    }

    @Getter
    @Setter
    public static class RedisFanout {

        private boolean enabled = true;

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9:._-]{1,120}$", message = "contains unsupported Redis channel characters")
        private String channel = "bourse-azma:websocket:events";
    }
}
