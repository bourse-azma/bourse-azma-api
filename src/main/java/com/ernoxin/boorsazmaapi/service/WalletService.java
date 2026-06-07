package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.UserResponse;
import com.ernoxin.boorsazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.boorsazmaapi.dto.WalletTransactionResponse;

import java.util.List;

public interface WalletService {
    UserResponse adjustBalance(Long userId, WalletAdjustmentRequest request);
    List<WalletTransactionResponse> getTransactions(Long userId);
}
