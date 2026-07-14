package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import com.ernoxin.bourseazmaapi.service.TradingAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trading")
@RequiredArgsConstructor
public class TradingAccountController {

    private final TradingAccountService tradingAccountService;

    @GetMapping("/orders")
    public ApiResponse<PagedResponse<TradingOrderResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<OrderStatus> statuses) {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد",
                tradingAccountService.getOrders(currentUserId, page, size, statuses));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateOrderResult> createOrder(@Valid @RequestBody CreateTradingOrderRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.CREATED, "سفارش با موفقیت ثبت شد",
                tradingAccountService.createOrder(currentUserId, request));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<CancelOrderResult> cancelOrder(@PathVariable Long orderId) {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "سفارش با موفقیت لغو شد",
                tradingAccountService.cancelOrder(currentUserId, orderId));
    }

    @GetMapping("/portfolio")
    public ApiResponse<List<PortfolioHoldingResponse>> getPortfolio() {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", tradingAccountService.getPortfolio(currentUserId));
    }

    @GetMapping("/rules")
    public ApiResponse<TradingRulesResponse> getTradingRules() {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", tradingAccountService.getTradingRules());
    }

    @GetMapping("/order-book")
    public ApiResponse<PrivateOrderBookResponse> getOrderBook(@RequestParam String instrumentCode) {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد",
                tradingAccountService.getOrderBook(currentUserId, instrumentCode));
    }
}
