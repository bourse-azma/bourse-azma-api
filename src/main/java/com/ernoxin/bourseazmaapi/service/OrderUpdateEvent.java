package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.TradingOrderResponse;

public record OrderUpdateEvent(String username, TradingOrderResponse order) {
}
