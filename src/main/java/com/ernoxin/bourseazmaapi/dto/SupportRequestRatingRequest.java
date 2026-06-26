package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportRequestRatingRequest {

    @NotNull(message = "امتیاز الزامی است.")
    @Min(value = 1, message = "امتیاز باید بین ۱ تا ۵ باشد.")
    @Max(value = 5, message = "امتیاز باید بین ۱ تا ۵ باشد.")
    private Integer rating;

    @Size(max = 500, message = "توضیح امتیاز حداکثر 500 کاراکتر است.")
    private String comment;
}
