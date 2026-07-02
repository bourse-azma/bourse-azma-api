package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.IndustrySummaryResponse;
import com.ernoxin.bourseazmaapi.dto.IndustrySymbolsResult;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.service.marketsearch.MarketCsvLoader;
import com.ernoxin.bourseazmaapi.service.marketsearch.MarketSearchMatcher;
import com.ernoxin.bourseazmaapi.service.marketsearch.MarketSearchNormalizer;
import com.ernoxin.bourseazmaapi.service.marketsearch.MarketSearchResultCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketSearchServiceImpl implements MarketSearchService {

    private final MarketCsvLoader marketCsvLoader;
    private final MarketSearchMatcher marketSearchMatcher;
    private final MarketSearchResultCache searchResultCache;

    @Override
    public List<Map<String, Object>> search(String query) {
        List<String> queryTokens = MarketSearchNormalizer.tokenizeQuery(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String cacheKey = String.join("\u0000", queryTokens);
        List<Map<String, Object>> cached = searchResultCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        MarketCsvLoader.CsvCache csvCache = marketCsvLoader.getCsvCache();
        List<Map<String, Object>> results = marketSearchMatcher.match(
                csvCache.searchIndex(),
                queryTokens,
                csvCache.oldInscodesHeader()
        );

        if (results.isEmpty()) {
            return results;
        }

        searchResultCache.put(cacheKey, results);
        return results;
    }

    @Override
    public List<IndustrySummaryResponse> getIndustries() {
        return marketCsvLoader.getCsvCache().industrySummaries();
    }

    @Override
    public IndustrySymbolsResult getIndustrySymbols(String industry) {
        if (industry == null || industry.isBlank()) {
            throw new ResourceNotFoundException("صنعت مورد نظر یافت نشد.");
        }

        String requestedIndustry = industry.trim();
        MarketCsvLoader.CsvCache csvCache = marketCsvLoader.getCsvCache();
        List<com.ernoxin.bourseazmaapi.dto.IndustrySymbolResponse> symbols =
                csvCache.industrySymbols().get(requestedIndustry);
        if (symbols == null || symbols.isEmpty()) {
            throw new ResourceNotFoundException("صنعت مورد نظر یافت نشد.");
        }
        return new IndustrySymbolsResult(requestedIndustry, symbols);
    }
}
