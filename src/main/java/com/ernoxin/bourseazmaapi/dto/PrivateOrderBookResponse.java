package com.ernoxin.bourseazmaapi.dto;

import java.time.Instant;
import java.util.List;

public record PrivateOrderBookResponse(
        String instrumentCode,
        List<PrivateOrderBookLevelResponse> rows,
        Instant refreshedAt
) {
}
