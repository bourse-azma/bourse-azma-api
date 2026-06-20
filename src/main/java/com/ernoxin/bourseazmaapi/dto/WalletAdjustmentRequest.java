package com.ernoxin.bourseazmaapi.dto;

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
    private String type; // SET, ADD, SUBTRACT, PERCENT_ADD, PERCENT_SUBTRACT

    @NotNull(message = "مبلغ یا درصد نباید خالی باشد.")
    private BigDecimal value;

    private String description;
}
