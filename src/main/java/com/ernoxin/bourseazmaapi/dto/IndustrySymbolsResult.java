package com.ernoxin.bourseazmaapi.dto;

import java.util.List;

public record IndustrySymbolsResult(
        String industry,
        List<IndustrySymbolResponse> symbols
) {
}
