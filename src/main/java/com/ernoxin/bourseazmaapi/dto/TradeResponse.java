package com.ernoxin.bourseazmaapi.dto;

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
