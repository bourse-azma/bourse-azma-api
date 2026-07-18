package com.ernoxin.bourseazmaapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record MarketDataUpdate(
        String instrumentCode,
        JsonNode closingPrice,
        JsonNode bestLimits,
        JsonNode clientType,
        Instant publishedAt
) {
}
