package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.IndustrySummaryResponse;
import com.ernoxin.bourseazmaapi.dto.IndustrySymbolsResult;

import java.util.List;
import java.util.Map;

public interface MarketSearchService {
    List<Map<String, Object>> search(String query);

    List<IndustrySummaryResponse> getIndustries();

    IndustrySymbolsResult getIndustrySymbols(String industry);
}
