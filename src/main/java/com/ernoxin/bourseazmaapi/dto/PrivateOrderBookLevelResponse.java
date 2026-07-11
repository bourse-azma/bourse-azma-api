package com.ernoxin.bourseazmaapi.dto;

import java.math.BigDecimal;

public record PrivateOrderBookLevelResponse(
        int level,
        BigDecimal askPrice,
        long askVolume,
        long askOrderCount,
        long ownAskVolume,
        BigDecimal bidPrice,
        long bidVolume,
        long bidOrderCount,
        long ownBidVolume
) {
}
