package com.ernoxin.boorsazmaapi.dto;

import java.util.List;

public record CreateOrderResult(
        TradingOrderResponse order,
        List<TradeResponse> trades
) {
}
