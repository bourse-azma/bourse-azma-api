package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.WatchlistCreateRequest;
import com.ernoxin.bourseazmaapi.dto.WatchlistResponse;
import com.ernoxin.bourseazmaapi.dto.WatchlistSymbolCreateRequest;
import com.ernoxin.bourseazmaapi.dto.WatchlistUpdateRequest;

import java.util.List;

public interface WatchlistService {

    List<WatchlistResponse> getCurrentUserWatchlists();

    WatchlistResponse create(WatchlistCreateRequest request);

    WatchlistResponse update(Long watchlistId, WatchlistUpdateRequest request);

    void delete(Long watchlistId);

    WatchlistResponse addSymbol(Long watchlistId, WatchlistSymbolCreateRequest request);

    WatchlistResponse removeSymbol(Long watchlistId, Long symbolId);
}
