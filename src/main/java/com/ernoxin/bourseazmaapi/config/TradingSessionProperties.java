package com.ernoxin.bourseazmaapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.trading.session")
public class TradingSessionProperties {

    private String timezone = "Asia/Tehran";
    private String endTime = "12:30";

    public LocalTime endLocalTime() {
        return LocalTime.parse(endTime);
    }
}
