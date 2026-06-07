package com.ernoxin.boorsazmaapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class WalletAdjustmentRequest {

    @NotBlank(message = "نوع عملیات نباید خالی باشد.")
    private String type; // SET, ADD, SUBTRACT, PERCENT_ADD, PERCENT_SUBTRACT

    @NotNull(message = "مبلغ یا درصد نباید خالی باشد.")
    private BigDecimal value;

    private String description;
}
