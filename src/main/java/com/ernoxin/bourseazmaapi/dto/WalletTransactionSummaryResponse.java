package com.ernoxin.bourseazmaapi.dto;

import java.math.BigDecimal;

public record WalletTransactionSummaryResponse(
        long totalCount,
        BigDecimal totalNet,
        long inflowCount,
        long outflowCount
) {
}
