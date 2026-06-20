package com.ernoxin.bourseazmaapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioHoldingResponse(
        Long id,
        Instant acquiredAt,
        String symbol,
        String instrumentCode,
        Long quantity,
        BigDecimal buyPrice,
        BigDecimal livePrice,
        BigDecimal netValue
) {
}
