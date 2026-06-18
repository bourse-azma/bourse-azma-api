package com.ernoxin.boorsazmaapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
        Long id,
        Long quantity,
        BigDecimal price,
        BigDecimal value,
        Instant executedAt
) {
}
