package com.ernoxin.bourseazmaapi.dto;

import com.ernoxin.bourseazmaapi.model.*;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingOrderResponse(
        Long id,
        OrderSide side,
        String sideLabel,
        String symbol,
        String instrumentCode,
        Long quantity,
        Long remainingQuantity,
        Long executedQuantity,
        BigDecimal orderPrice,
        BigDecimal livePrice,
        BigDecimal averageExecutedPrice,
        BigDecimal orderValue,
        Instant orderTime,
        Instant cancelledAt,
        OrderStatus status,
        String statusLabel,
        boolean cancellable,
        OrderType orderType,
        String orderTypeLabel,
        PriceType priceType,
        OrderValidity validity,
        Instant expiresAt,
        TriggerComparator triggerComparator,
        BigDecimal triggerPrice
) {
}
