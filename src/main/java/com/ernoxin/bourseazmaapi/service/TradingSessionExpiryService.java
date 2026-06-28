package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.TradingSessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class TradingSessionExpiryService {

    private final TradingSessionProperties tradingSessionProperties;

    public Instant endOfCurrentTradingSession(Instant from) {
        ZoneId zone = ZoneId.of(tradingSessionProperties.getTimezone());
        ZonedDateTime zoned = from.atZone(zone);
        ZonedDateTime sessionEnd = zoned.toLocalDate()
                .atTime(tradingSessionProperties.endLocalTime())
                .atZone(zone);
        return sessionEnd.toInstant();
    }
}
