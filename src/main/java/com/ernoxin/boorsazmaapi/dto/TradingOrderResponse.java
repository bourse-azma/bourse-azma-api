package com.ernoxin.boorsazmaapi.dto;

import com.ernoxin.boorsazmaapi.model.OrderSide;
import com.ernoxin.boorsazmaapi.model.OrderStatus;
import com.ernoxin.boorsazmaapi.model.OrderType;
import com.ernoxin.boorsazmaapi.model.OrderValidity;
import com.ernoxin.boorsazmaapi.model.PriceType;
import com.ernoxin.boorsazmaapi.model.TriggerComparator;

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
