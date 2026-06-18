package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.IndustrySummaryResponse;
import com.ernoxin.boorsazmaapi.dto.IndustrySymbolsResult;

import java.util.List;
import java.util.Map;

public interface MarketSearchService {
    List<Map<String, Object>> search(String query);

    List<IndustrySummaryResponse> getIndustries();

    IndustrySymbolsResult getIndustrySymbols(String industry);
}
