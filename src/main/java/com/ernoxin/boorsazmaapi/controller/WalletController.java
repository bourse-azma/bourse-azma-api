package com.ernoxin.boorsazmaapi.controller;

import com.ernoxin.boorsazmaapi.dto.UserResponse;
import com.ernoxin.boorsazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.boorsazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.boorsazmaapi.dto.api.ApiResponse;
import com.ernoxin.boorsazmaapi.security.SecurityUtils;
import com.ernoxin.boorsazmaapi.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/adjust")
    public ApiResponse<UserResponse> adjustBalance(@Valid @RequestBody WalletAdjustmentRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", walletService.adjustBalance(currentUserId, request));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<WalletTransactionResponse>> getTransactions() {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", walletService.getTransactions(currentUserId));
    }
}
