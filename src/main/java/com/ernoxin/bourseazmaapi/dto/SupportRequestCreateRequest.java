package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportRequestCreateRequest {

    @NotBlank(message = "موضوع درخواست الزامی است.")
    @Size(max = 120, message = "موضوع درخواست حداکثر 120 کاراکتر است.")
    private String subject;

    @NotBlank(message = "متن پیام الزامی است.")
    @Size(max = 2000, message = "متن پیام حداکثر 2000 کاراکتر است.")
    private String message;
}
