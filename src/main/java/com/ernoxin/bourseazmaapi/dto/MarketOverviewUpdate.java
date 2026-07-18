package com.ernoxin.bourseazmaapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record MarketOverviewUpdate(
        int marketId,
        JsonNode marketOverview,
        Instant publishedAt
) {
}
