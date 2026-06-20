package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;

import java.util.List;

public interface WalletService {
    UserResponse adjustBalance(Long userId, WalletAdjustmentRequest request);

    List<WalletTransactionResponse> getTransactions(Long userId);
}
