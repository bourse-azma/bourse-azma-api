package com.ernoxin.bourseazmaapi.dto.admin;

import com.ernoxin.bourseazmaapi.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTradeResponse(
        Long id,
        String symbol,
        String instrumentCode,
        OrderSide side,
        Long quantity,
        BigDecimal price,
        BigDecimal value,
        Instant executedAt
) {
}
