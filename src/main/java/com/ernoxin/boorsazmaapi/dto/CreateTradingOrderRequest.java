package com.ernoxin.boorsazmaapi.dto;

import com.ernoxin.boorsazmaapi.model.OrderSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTradingOrderRequest {

    @NotNull(message = "نوع سفارش الزامی است.")
    private OrderSide side;

    @NotBlank(message = "نماد الزامی است.")
    @Size(max = 50, message = "نماد حداکثر 50 کاراکتر است.")
    private String symbol;

    @NotBlank(message = "کد ابزار الزامی است.")
    @Size(max = 60, message = "کد ابزار حداکثر 60 کاراکتر است.")
    private String instrumentCode;

    @NotNull(message = "تعداد الزامی است.")
    @Positive(message = "تعداد باید بزرگ‌تر از صفر باشد.")
    private Long quantity;

    @NotNull(message = "قیمت سفارش الزامی است.")
    @DecimalMin(value = "0.01", message = "قیمت سفارش باید بزرگ‌تر از صفر باشد.")
    private BigDecimal orderPrice;

    @NotNull(message = "قیمت لحظه‌ای الزامی است.")
    @DecimalMin(value = "0.01", message = "قیمت لحظه‌ای باید بزرگ‌تر از صفر باشد.")
    private BigDecimal livePrice;
}
