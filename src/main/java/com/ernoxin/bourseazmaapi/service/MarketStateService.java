package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.client.TsetmcMarketClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketStateService {

    private static final String OPEN_STATE_TITLE = "باز";

    private final TsetmcMarketClient tsetmcMarketClient;

    public boolean isMarketOpen() {
        return isMarketOpen(1) || isMarketOpen(2);
    }

    private boolean isMarketOpen(int marketId) {
        return tsetmcMarketClient.getMarketOverview(marketId)
                .map(this::extractMarketStateTitle)
                .map(OPEN_STATE_TITLE::equals)
                .orElse(false);
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
