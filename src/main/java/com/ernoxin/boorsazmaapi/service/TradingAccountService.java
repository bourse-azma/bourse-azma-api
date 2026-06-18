package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.CancelOrderResult;
import com.ernoxin.boorsazmaapi.dto.CreateOrderResult;
import com.ernoxin.boorsazmaapi.dto.CreateTradingOrderRequest;
import com.ernoxin.boorsazmaapi.dto.PortfolioHoldingResponse;
import com.ernoxin.boorsazmaapi.dto.TradingOrderResponse;

import java.util.List;

public interface TradingAccountService {
    List<TradingOrderResponse> getOrders(Long userId);

    List<PortfolioHoldingResponse> getPortfolio(Long userId);

    CreateOrderResult createOrder(Long userId, CreateTradingOrderRequest request);

    CancelOrderResult cancelOrder(Long userId, Long orderId);
}
