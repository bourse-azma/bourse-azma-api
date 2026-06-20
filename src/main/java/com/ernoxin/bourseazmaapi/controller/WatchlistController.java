package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.WatchlistCreateRequest;
import com.ernoxin.bourseazmaapi.dto.WatchlistResponse;
import com.ernoxin.bourseazmaapi.dto.WatchlistSymbolCreateRequest;
import com.ernoxin.bourseazmaapi.dto.WatchlistUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlists")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ApiResponse<List<WatchlistResponse>> getCurrentUserWatchlists() {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", watchlistService.getCurrentUserWatchlists());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WatchlistResponse> create(@Valid @RequestBody WatchlistCreateRequest request) {
        return ApiResponse.of(HttpStatus.CREATED, "عملیات با موفقیت انجام شد", watchlistService.create(request));
    }

    @PutMapping("/{watchlistId}")
    public ApiResponse<WatchlistResponse> update(@PathVariable Long watchlistId,
                                                 @Valid @RequestBody WatchlistUpdateRequest request) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", watchlistService.update(watchlistId, request));
    }

    @DeleteMapping("/{watchlistId}")
    public ApiResponse<Void> delete(@PathVariable Long watchlistId) {
        watchlistService.delete(watchlistId);
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", null);
    }

    @PostMapping("/{watchlistId}/symbols")
    public ApiResponse<WatchlistResponse> addSymbol(@PathVariable Long watchlistId,
                                                    @Valid @RequestBody WatchlistSymbolCreateRequest request) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", watchlistService.addSymbol(watchlistId, request));
    }

    @DeleteMapping("/{watchlistId}/symbols/{symbolId}")
    public ApiResponse<WatchlistResponse> removeSymbol(@PathVariable Long watchlistId,
                                                       @PathVariable Long symbolId) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", watchlistService.removeSymbol(watchlistId, symbolId));
    }

}
