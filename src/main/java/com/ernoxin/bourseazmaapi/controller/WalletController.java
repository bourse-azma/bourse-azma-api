package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionSummaryResponse;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import com.ernoxin.bourseazmaapi.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/adjust")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> adjustBalance(@Valid @RequestBody WalletAdjustmentRequest request) {
        Long targetUserId = resolveTargetUserId(request.getUserId());
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", walletService.adjustBalance(targetUserId, request));
    }

    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PagedResponse<WalletTransactionResponse>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(
                HttpStatus.OK,
                "عملیات با موفقیت انجام شد",
                walletService.getTransactions(currentUserId, page, size)
        );
    }

    @GetMapping("/transactions/summary")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<WalletTransactionSummaryResponse> getTransactionSummary() {
        Long currentUserId = SecurityUtils.currentUserId();
        return ApiResponse.of(
                HttpStatus.OK,
                "عملیات با موفقیت انجام شد",
                walletService.getTransactionSummary(currentUserId)
        );
    }

    private Long resolveTargetUserId(Long requestedUserId) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (SecurityUtils.currentUserRole() == UserRole.ADMIN) {
            return requestedUserId != null ? requestedUserId : currentUserId;
        }
        if (requestedUserId != null && !requestedUserId.equals(currentUserId)) {
            throw new AccessDeniedException("عدم دسترسی");
        }
        return currentUserId;
    }
}
