package com.ernoxin.boorsazmaapi.dto;

import java.util.List;

public record IndustrySymbolsResult(
        String industry,
        List<IndustrySymbolResponse> symbols
) {
}
