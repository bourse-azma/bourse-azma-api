package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.model.SupportRequestCategory;
import com.ernoxin.bourseazmaapi.model.SupportRequestPriority;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import com.ernoxin.bourseazmaapi.service.SupportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/support-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportRequestController {

    private final SupportRequestService supportRequestService;

    @GetMapping
    public ApiResponse<List<SupportRequestResponse>> getAll(
            @RequestParam(required = false) SupportRequestStatus status,
            @RequestParam(required = false) SupportRequestCategory category,
            @RequestParam(required = false) SupportRequestPriority priority
    ) {
        return ApiResponse.of(
                HttpStatus.OK,
                "عملیات با موفقیت انجام شد",
                supportRequestService.getAllRequests(status, category, priority)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<SupportRequestDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.getAdminRequestDetail(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<SupportRequestMessageResponse> addMessage(
            @PathVariable Long id,
            @Valid @RequestBody SupportRequestMessageCreateRequest request
    ) {
        return ApiResponse.of(HttpStatus.CREATED, "عملیات با موفقیت انجام شد", supportRequestService.addAdminMessage(id, request));
    }

    @PatchMapping("/{id}/messages/{messageId}")
    public ApiResponse<SupportRequestMessageResponse> editMessage(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @Valid @RequestBody SupportRequestMessageUpdateRequest request
    ) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.editAdminMessage(id, messageId, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<SupportRequestResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody SupportRequestStatusUpdateRequest request
    ) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.updateStatus(id, request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<SupportRequestResponse> close(@PathVariable Long id) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", supportRequestService.closeAdminRequest(id));
    }
}
