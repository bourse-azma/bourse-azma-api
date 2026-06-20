package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WatchlistCreateRequest {

    @NotBlank(message = "نام دیده بان الزامی است.")
    @Size(max = 80, message = "نام دیده بان حداکثر 80 کاراکتر است.")
    private String name;
}
