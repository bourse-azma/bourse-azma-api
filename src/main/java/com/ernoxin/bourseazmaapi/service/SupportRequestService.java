package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.SupportRequestCreateRequest;
import com.ernoxin.bourseazmaapi.dto.SupportRequestResponse;

import java.util.List;

public interface SupportRequestService {
    List<SupportRequestResponse> getCurrentUserRequests();

    SupportRequestResponse create(SupportRequestCreateRequest request);
}
