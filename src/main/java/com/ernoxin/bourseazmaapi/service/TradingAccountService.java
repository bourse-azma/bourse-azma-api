package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.model.OrderStatus;

import java.util.List;

public interface TradingAccountService {
    PagedResponse<TradingOrderResponse> getOrders(Long userId, int page, int size, List<OrderStatus> statuses);

    List<PortfolioHoldingResponse> getPortfolio(Long userId);

    CreateOrderResult createOrder(Long userId, CreateTradingOrderRequest request);

    CancelOrderResult cancelOrder(Long userId, Long orderId);
}
