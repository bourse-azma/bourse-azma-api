package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.SupportRequestCreateRequest;
import com.ernoxin.bourseazmaapi.dto.SupportRequestResponse;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.service.SupportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support-requests")
@RequiredArgsConstructor
public class SupportRequestController {

    private final SupportRequestService supportRequestService;

    @GetMapping
    public ApiResponse<List<SupportRequestResponse>> getCurrentUserRequests() {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.getCurrentUserRequests());
    }

    @PostMapping
    public ApiResponse<SupportRequestResponse> create(@Valid @RequestBody SupportRequestCreateRequest request) {
        return ApiResponse.of(HttpStatus.CREATED, "عملیات با موفقیت انجام شد", supportRequestService.create(request));
    }
}
