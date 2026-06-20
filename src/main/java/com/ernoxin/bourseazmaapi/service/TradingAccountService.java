package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.*;

import java.util.List;

public interface TradingAccountService {
    List<TradingOrderResponse> getOrders(Long userId);

    List<PortfolioHoldingResponse> getPortfolio(Long userId);

    CreateOrderResult createOrder(Long userId, CreateTradingOrderRequest request);

    CancelOrderResult cancelOrder(Long userId, Long orderId);
}
