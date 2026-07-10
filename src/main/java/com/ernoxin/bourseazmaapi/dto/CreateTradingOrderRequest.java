package com.ernoxin.bourseazmaapi.dto;

import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderType;
import com.ernoxin.bourseazmaapi.model.PriceType;
import com.ernoxin.bourseazmaapi.model.TriggerComparator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTradingOrderRequest {

    @NotNull(message = "سمت سفارش (خرید/فروش) الزامی است.")
    private OrderSide side;

    @NotNull(message = "نوع سفارش (عادی/شرطی) الزامی است.")
    private OrderType orderType;

    @NotNull(message = "نوع قیمت (دلخواه/بازار) الزامی است.")
    private PriceType priceType;

    @NotBlank(message = "نماد الزامی است.")
    @Size(max = 50, message = "نماد حداکثر 50 کاراکتر است.")
    private String symbol;

    @NotBlank(message = "کد ابزار الزامی است.")
    @Size(max = 60, message = "کد ابزار حداکثر 60 کاراکتر است.")
    private String instrumentCode;

    @NotNull(message = "تعداد الزامی است.")
    @Positive(message = "تعداد باید بزرگ‌تر از صفر باشد.")
    private Long quantity;

    /**
     * Required only when {@link #priceType} is {@code CUSTOM}; ignored for market orders.
     */
    @DecimalMin(value = "0.01", message = "قیمت باید بزرگ‌تر از صفر باشد.")
    private BigDecimal price;

    /**
     * Best available market price captured by the client at submission time. Used as the
     * effective price for market orders and to validate buying power / order value.
     */
    @NotNull(message = "قیمت لحظه‌ای الزامی است.")
    @DecimalMin(value = "0.01", message = "قیمت لحظه‌ای باید بزرگ‌تر از صفر باشد.")
    private BigDecimal livePrice;

    @Valid
    private TriggerRequest trigger;

    @Data
    public static class TriggerRequest {

        @NotNull(message = "شرط قیمت الزامی است.")
        private TriggerComparator comparator;

        @NotNull(message = "قیمت شرط الزامی است.")
        @DecimalMin(value = "0.01", message = "قیمت شرط باید بزرگ‌تر از صفر باشد.")
        private BigDecimal price;
    }
}
