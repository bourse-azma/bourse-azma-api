package com.ernoxin.boorsazmaapi.controller;

import com.ernoxin.boorsazmaapi.dto.UserResponse;
import com.ernoxin.boorsazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.boorsazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.boorsazmaapi.dto.api.ApiResponse;
import com.ernoxin.boorsazmaapi.model.UserRole;
import com.ernoxin.boorsazmaapi.security.SecurityUtils;
import com.ernoxin.boorsazmaapi.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/adjust")
    public ApiResponse<UserResponse> adjustBalance(@Valid @RequestBody WalletAdjustmentRequest request) {
        Long targetUserId = resolveTargetUserId(request.getUserId());
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", walletService.adjustBalance(targetUserId, request));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<WalletTransactionResponse>> getTransactions() {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", walletService.getTransactions(currentUserId));
    }

    private Long resolveTargetUserId(Long requestedUserId) {
        if (SecurityUtils.currentUserRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("عدم دسترسی");
        }
        return requestedUserId != null ? requestedUserId : SecurityUtils.currentUserId();
    }
}
