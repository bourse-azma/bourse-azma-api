package com.ernoxin.bourseazmaapi.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminBalanceUpdateRequest(
        @NotNull(message = "موجودی جدید نباید خالی باشد.")
        @DecimalMin(value = "0", message = "موجودی نمی‌تواند منفی باشد.")
        BigDecimal balance,
        @Size(max = 1000, message = "یادداشت مدیر حداکثر ۱۰۰۰ کاراکتر است.")
        String note
) {
}
