package com.ernoxin.bourseazmaapi.dto;

import java.util.List;

public record CreateOrderResult(
        TradingOrderResponse order,
        List<TradeResponse> trades
) {
}
