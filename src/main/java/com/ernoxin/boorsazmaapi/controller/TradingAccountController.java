package com.ernoxin.boorsazmaapi.controller;

import com.ernoxin.boorsazmaapi.dto.PortfolioHoldingResponse;
import com.ernoxin.boorsazmaapi.dto.TradingOrderResponse;
import com.ernoxin.boorsazmaapi.dto.api.ApiResponse;
import com.ernoxin.boorsazmaapi.security.SecurityUtils;
import com.ernoxin.boorsazmaapi.service.TradingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/portfolio")
    public ApiResponse<List<PortfolioHoldingResponse>> getPortfolio() {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", tradingAccountService.getPortfolio(currentUserId));
    }
}
