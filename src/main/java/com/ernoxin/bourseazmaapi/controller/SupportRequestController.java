package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.*;
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

    @GetMapping("/{id}")
    public ApiResponse<SupportRequestDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.getCurrentUserRequestDetail(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<SupportRequestMessageResponse> addMessage(
            @PathVariable Long id,
            @Valid @RequestBody SupportRequestMessageCreateRequest request
    ) {
        return ApiResponse.of(HttpStatus.CREATED, "عملیات با موفقیت انجام شد", supportRequestService.addUserMessage(id, request));
    }

    @PatchMapping("/{id}/messages/{messageId}")
    public ApiResponse<SupportRequestMessageResponse> editMessage(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @Valid @RequestBody SupportRequestMessageUpdateRequest request
    ) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.editUserMessage(id, messageId, request));
    }

    @PatchMapping("/{id}/initial-message")
    public ApiResponse<SupportRequestMessageResponse> editInitialMessage(
            @PathVariable Long id,
            @Valid @RequestBody SupportRequestMessageUpdateRequest request
    ) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.editUserInitialMessage(id, request));
    }

    @PostMapping("/{id}/rating")
    public ApiResponse<SupportRequestResponse> rate(
            @PathVariable Long id,
            @Valid @RequestBody SupportRequestRatingRequest request
    ) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.rateRequest(id, request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<SupportRequestResponse> close(@PathVariable Long id) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.closeCurrentUserRequest(id));
    }
}
