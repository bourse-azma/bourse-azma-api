package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import com.ernoxin.bourseazmaapi.service.WalletService;
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
