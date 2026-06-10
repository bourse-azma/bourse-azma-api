package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.WatchlistCreateRequest;
import com.ernoxin.boorsazmaapi.dto.WatchlistResponse;
import com.ernoxin.boorsazmaapi.dto.WatchlistSymbolCreateRequest;
import com.ernoxin.boorsazmaapi.dto.WatchlistUpdateRequest;

import java.util.List;

public interface WatchlistService {

    List<WatchlistResponse> getCurrentUserWatchlists();

    WatchlistResponse create(WatchlistCreateRequest request);

    WatchlistResponse update(Long watchlistId, WatchlistUpdateRequest request);

    void delete(Long watchlistId);

    WatchlistResponse addSymbol(Long watchlistId, WatchlistSymbolCreateRequest request);

    WatchlistResponse removeSymbol(Long watchlistId, Long symbolId);
}
