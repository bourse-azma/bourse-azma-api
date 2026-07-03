package com.ernoxin.bourseazmaapi.dto.admin;

import com.ernoxin.bourseazmaapi.dto.PortfolioHoldingResponse;
import com.ernoxin.bourseazmaapi.dto.TradingOrderResponse;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;

import java.util.List;

public record AdminUserDetailResponse(
        AdminUserSummaryResponse user,
        List<TradingOrderResponse> orders,
        List<AdminTradeResponse> trades,
        List<PortfolioHoldingResponse> portfolio,
        List<WalletTransactionResponse> walletTransactions,
        List<AdminActivityResponse> activities
) {
}
