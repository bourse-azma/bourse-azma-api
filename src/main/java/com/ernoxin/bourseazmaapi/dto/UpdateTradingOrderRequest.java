package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateTradingOrderRequest {

    @NotNull(message = "تعداد الزامی است.")
    @Positive(message = "تعداد باید بزرگ‌تر از صفر باشد.")
    private Long quantity;

    /**
     * The new price per share for custom-price orders. Market-price orders ignore this field.
     */
    @DecimalMin(value = "0.01", message = "قیمت باید بزرگ‌تر از صفر باشد.")
    private BigDecimal price;
}
