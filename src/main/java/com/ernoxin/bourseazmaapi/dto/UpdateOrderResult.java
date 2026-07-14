package com.ernoxin.bourseazmaapi.dto;

import java.util.List;

public record UpdateOrderResult(
        TradingOrderResponse order,
        List<TradeResponse> trades
) {
}
