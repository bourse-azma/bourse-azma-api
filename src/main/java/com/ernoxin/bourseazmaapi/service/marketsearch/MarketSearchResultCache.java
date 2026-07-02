package com.ernoxin.bourseazmaapi.service.marketsearch;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MarketSearchResultCache {

    private static final int SEARCH_CACHE_MAX_ENTRIES = 256;

    private final Map<String, List<Map<String, Object>>> cache =
            Collections.synchronizedMap(new LinkedHashMap<>(SEARCH_CACHE_MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Map<String, Object>>> eldest) {
                    return size() > SEARCH_CACHE_MAX_ENTRIES;
                }
            });

    public List<Map<String, Object>> get(String cacheKey) {
        return cache.get(cacheKey);
    }

    public void put(String cacheKey, List<Map<String, Object>> results) {
        cache.put(cacheKey, results);
    }

    public void clear() {
        cache.clear();
    }
}
