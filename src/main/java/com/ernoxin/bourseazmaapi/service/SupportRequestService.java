package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.model.SupportRequestCategory;
import com.ernoxin.bourseazmaapi.model.SupportRequestPriority;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;

import java.util.List;

public interface SupportRequestService {
    List<SupportRequestResponse> getCurrentUserRequests();

    SupportRequestResponse create(SupportRequestCreateRequest request);

    SupportRequestDetailResponse getCurrentUserRequestDetail(Long id);

    SupportRequestMessageResponse addUserMessage(Long id, SupportRequestMessageCreateRequest request);

    SupportRequestMessageResponse editUserMessage(Long id, Long messageId, SupportRequestMessageUpdateRequest request);

    SupportRequestMessageResponse editUserInitialMessage(Long id, SupportRequestMessageUpdateRequest request);

    SupportRequestResponse rateRequest(Long id, SupportRequestRatingRequest request);

    SupportRequestResponse closeCurrentUserRequest(Long id);

    List<SupportRequestResponse> getAllRequests(
            SupportRequestStatus status,
            SupportRequestCategory category,
            SupportRequestPriority priority
    );

    SupportRequestDetailResponse getAdminRequestDetail(Long id);

    SupportRequestMessageResponse addAdminMessage(Long id, SupportRequestMessageCreateRequest request);

    SupportRequestMessageResponse editAdminMessage(Long id, Long messageId, SupportRequestMessageUpdateRequest request);

    SupportRequestResponse updateStatus(Long id, SupportRequestStatusUpdateRequest request);

    SupportRequestResponse closeAdminRequest(Long id);
}
