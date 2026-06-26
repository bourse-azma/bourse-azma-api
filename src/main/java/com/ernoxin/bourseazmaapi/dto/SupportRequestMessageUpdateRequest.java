package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportRequestMessageUpdateRequest {

    @NotBlank(message = "متن پیام الزامی است.")
    @Size(max = 2000, message = "متن پیام حداکثر 2000 کاراکتر است.")
    private String message;
}
