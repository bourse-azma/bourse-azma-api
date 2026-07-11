package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.PortfolioHoldingResponse;
import com.ernoxin.bourseazmaapi.dto.TradingOrderResponse;
import com.ernoxin.bourseazmaapi.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
class TradingAccountResponseMapper {

    TradingOrderResponse toOrderResponse(TradingOrder order) {
        BigDecimal orderValue = order.getOrderPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
        return new TradingOrderResponse(
                order.getId(),
                order.getSide(),
                sideLabel(order.getSide()),
                order.getSymbol(),
                order.getInstrumentCode(),
                order.getQuantity(),
                order.getRemainingQuantity(),
                order.getExecutedQuantity(),
                order.getOrderPrice(),
                order.getLivePrice(),
                order.getAverageExecutedPrice(),
                orderValue,
                order.getOrderTime(),
                order.getCancelledAt(),
                order.getStatus(),
                statusLabel(order.getStatus()),
                order.isCancellable(),
                order.getOrderType(),
                orderTypeLabel(order.getOrderType()),
                order.getPriceType(),
                order.getTriggerComparator(),
                order.getTriggerPrice()
        );
    }

    PortfolioHoldingResponse toPortfolioResponse(PortfolioHolding holding, BigDecimal livePrice) {
        BigDecimal netValue = livePrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
        return new PortfolioHoldingResponse(
                holding.getId(),
                holding.getAcquiredAt(),
                holding.getSymbol(),
                holding.getInstrumentCode(),
                holding.getQuantity(),
                holding.getBuyPrice(),
                livePrice,
                netValue
        );
    }

    private String sideLabel(OrderSide side) {
        return switch (side) {
            case BUY -> "خرید";
            case SELL -> "فروش";
        };
    }

    private String orderTypeLabel(OrderType orderType) {
        if (orderType == null) {
            return "سفارش عادی";
        }
        return switch (orderType) {
            case NORMAL -> "سفارش عادی";
            case CONDITIONAL -> "سفارش شرطی";
        };
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case REQUESTED -> "درخواست شده";
            case PARTIALLY_FILLED -> "اجرای جزئی";
            case COMPLETED -> "انجام شده";
            case CANCELLED -> "لغو شده";
            case FAILED -> "ناموفق";
            case TRIGGER_PENDING -> "در انتظار شرط";
        };
    }
}
