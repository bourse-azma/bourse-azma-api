package com.ernoxin.bourseazmaapi.dto;

import java.util.List;

public record WatchlistResponse(
        Long id,
        String name,
        List<WatchlistSymbolResponse> symbols
) {
}
