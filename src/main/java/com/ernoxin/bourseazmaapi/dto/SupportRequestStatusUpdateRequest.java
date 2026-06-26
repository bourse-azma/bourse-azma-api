package com.ernoxin.bourseazmaapi.dto;

import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportRequestStatusUpdateRequest {

    @NotNull(message = "وضعیت تیکت الزامی است.")
    private SupportRequestStatus status;
}
