package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    private String description;
}
