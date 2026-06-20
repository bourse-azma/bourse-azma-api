package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WatchlistSymbolCreateRequest {

    @NotBlank(message = "کلید نماد الزامی است.")
    @Size(max = 120, message = "کلید نماد حداکثر 120 کاراکتر است.")
    private String symbolKey;

    @NotBlank(message = "نماد الزامی است.")
    @Size(max = 50, message = "نماد حداکثر 50 کاراکتر است.")
    private String symbol;

    @NotBlank(message = "نام نماد الزامی است.")
    @Size(max = 160, message = "نام نماد حداکثر 160 کاراکتر است.")
    private String name;

    @Size(max = 20, message = "نوع بازار حداکثر 20 کاراکتر است.")
    private String sourceType;

    @Size(max = 60, message = "کد ابزار حداکثر 60 کاراکتر است.")
    private String instrumentCode;

    @Size(max = 40, message = "شناسه isin حداکثر 40 کاراکتر است.")
    private String isin;
}
