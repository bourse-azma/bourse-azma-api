package com.ernoxin.bourseazmaapi.dto;

import java.math.BigDecimal;

public record TradingRulesResponse(
        BigDecimal minimumOrderValue,
        BigDecimal maximumWalletAdjustment
) {
}
