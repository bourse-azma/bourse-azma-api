package com.ernoxin.boorsazmaapi.controller;

import com.ernoxin.boorsazmaapi.dto.CancelOrderResult;
import com.ernoxin.boorsazmaapi.dto.CreateOrderResult;
import com.ernoxin.boorsazmaapi.dto.CreateTradingOrderRequest;
import com.ernoxin.boorsazmaapi.dto.PortfolioHoldingResponse;
import com.ernoxin.boorsazmaapi.dto.TradingOrderResponse;
import com.ernoxin.boorsazmaapi.dto.api.ApiResponse;
import com.ernoxin.boorsazmaapi.security.SecurityUtils;
import com.ernoxin.boorsazmaapi.service.TradingAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trading")
@RequiredArgsConstructor
public class TradingAccountController {

    private final TradingAccountService tradingAccountService;

    @GetMapping("/orders")
    public ApiResponse<List<TradingOrderResponse>> getOrders() {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", tradingAccountService.getOrders(currentUserId));
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
}
