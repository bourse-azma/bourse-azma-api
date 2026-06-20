package com.ernoxin.bourseazmaapi.service;

import java.math.BigDecimal;

public record MarketLiquidityLevel(
        int levelNumber,
        BigDecimal price,
        long volume
) {
}
