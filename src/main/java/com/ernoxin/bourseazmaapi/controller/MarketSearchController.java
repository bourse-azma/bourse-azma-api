package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.IndustrySummaryResponse;
import com.ernoxin.bourseazmaapi.dto.IndustrySymbolsResult;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.service.MarketSearchService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market-search")
@RequiredArgsConstructor
@Validated
public class MarketSearchController {

    private final MarketSearchService marketSearchService;

    @GetMapping("/symbols")
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam("query") @NotBlank(message = "عبارت جستجو نباید خالی باشد.") String query
    ) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", marketSearchService.search(query));
    }

    @GetMapping("/industries")
    public ApiResponse<List<IndustrySummaryResponse>> getIndustries() {
        return ApiResponse.ok(marketSearchService.getIndustries());
    }

    @GetMapping("/industries/symbols")
    public ApiResponse<IndustrySymbolsResult> getIndustrySymbols(
            @RequestParam("industry") @NotBlank(message = "نام صنعت نباید خالی باشد.") String industry
    ) {
        return ApiResponse.ok(marketSearchService.getIndustrySymbols(industry));
    }
}
