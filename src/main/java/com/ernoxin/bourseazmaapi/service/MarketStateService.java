package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.client.TsetmcMarketClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarketStateService {

    private static final String OPEN_STATE_TITLE = "باز";

    private final TsetmcMarketClient tsetmcMarketClient;

    public boolean isMarketOpen() {
        return getSessionState() == MarketSessionState.OPEN;
    }

    public MarketSessionState getSessionState() {
        Optional<Boolean> bourse = loadOpenState(1);
        Optional<Boolean> farabourse = loadOpenState(2);
        if (bourse.orElse(false) || farabourse.orElse(false)) {
            return MarketSessionState.OPEN;
        }
        // Requiring both sources prevents a transient TSETMC/API outage from being treated
        // as an end-of-session event that would expire every user's orders.
        if (bourse.isPresent() && farabourse.isPresent()) {
            return MarketSessionState.CLOSED;
        }
        return MarketSessionState.UNKNOWN;
    }

    private Optional<Boolean> loadOpenState(int marketId) {
        return tsetmcMarketClient.getMarketOverview(marketId)
                .map(this::extractMarketStateTitle)
                .filter(title -> !title.isBlank())
                .map(OPEN_STATE_TITLE::equals);
    }

    private String extractMarketStateTitle(JsonNode result) {
        JsonNode overview = result.get("marketOverview");
        if (overview == null || overview.isMissingNode() || overview.isNull()) {
            return "";
        }
        JsonNode title = overview.get("marketStateTitle");
        return title == null || title.isNull() ? "" : title.asText("").trim();
    }
}
