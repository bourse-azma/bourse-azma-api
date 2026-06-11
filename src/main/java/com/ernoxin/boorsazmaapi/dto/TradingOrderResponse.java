package com.ernoxin.boorsazmaapi.dto;

import com.ernoxin.boorsazmaapi.model.OrderSide;
import com.ernoxin.boorsazmaapi.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingOrderResponse(
        Long id,
        OrderSide side,
        String sideLabel,
        String symbol,
        String instrumentCode,
        Long quantity,
        BigDecimal orderPrice,
        BigDecimal livePrice,
        Instant orderTime,
        OrderStatus status,
        String statusLabel
) {
}
