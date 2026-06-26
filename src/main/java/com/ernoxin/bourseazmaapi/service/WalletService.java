package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;

public interface WalletService {
    UserResponse adjustBalance(Long userId, WalletAdjustmentRequest request);

    PagedResponse<WalletTransactionResponse> getTransactions(Long userId, int page, int size);
}
