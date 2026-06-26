package com.ernoxin.bourseazmaapi.dto;

import java.util.List;

public record SupportRequestDetailResponse(
        SupportRequestResponse ticket,
        List<SupportRequestMessageResponse> messages
) {
}
