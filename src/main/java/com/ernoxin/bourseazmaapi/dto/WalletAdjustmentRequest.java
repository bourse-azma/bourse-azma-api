package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class WalletAdjustmentRequest {

    @Positive(message = "شناسه کاربر باید عددی مثبت باشد.")
    private Long userId;

    @NotBlank(message = "نوع عملیات نباید خالی باشد.")
    private String type; // ADD, SUBTRACT

    @NotNull(message = "مبلغ نباید خالی باشد.")
    @Positive(message = "مبلغ باید بزرگ‌تر از صفر باشد.")
    @DecimalMin(value = "0.01", message = "مبلغ باید بزرگ‌تر از صفر باشد.")
    private BigDecimal value;

    @Size(max = 255, message = "توضیحات حداکثر 255 کاراکتر است.")
    private String description;
}
