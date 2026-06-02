package com.ernoxin.boorsazmaapi.controller;

import com.ernoxin.boorsazmaapi.dto.WatchlistCreateRequest;
import com.ernoxin.boorsazmaapi.dto.WatchlistResponse;
import com.ernoxin.boorsazmaapi.dto.WatchlistSymbolCreateRequest;
import com.ernoxin.boorsazmaapi.dto.WatchlistUpdateRequest;
import com.ernoxin.boorsazmaapi.dto.api.ApiResponse;
import com.ernoxin.boorsazmaapi.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

}

