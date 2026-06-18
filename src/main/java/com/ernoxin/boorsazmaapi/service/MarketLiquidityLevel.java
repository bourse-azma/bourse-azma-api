package com.ernoxin.boorsazmaapi.service;

import java.math.BigDecimal;

public record MarketLiquidityLevel(
        int levelNumber,
        BigDecimal price,
        long volume
) {
}
