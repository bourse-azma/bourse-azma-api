package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "trading_orders", indexes = {
        @Index(name = "idx_order_private_book", columnList = "user_id,instrument_code,status,order_time"),
        @Index(name = "idx_order_matching", columnList = "status,instrument_code,remaining_quantity")
})
public class TradingOrder extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderSide side;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 60)
    private String instrumentCode;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Long remainingQuantity;

    @Column(nullable = false)
    private Long executedQuantity;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal orderPrice;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal livePrice;

    @Column(precision = 38, scale = 2)
    private BigDecimal averageExecutedPrice;

    @Column(nullable = false)
    private Instant orderTime;

    @Column
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private PriceType priceType;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TriggerComparator triggerComparator;

    @Column(precision = 38, scale = 2)
    private BigDecimal triggerPrice;

    public boolean isCancellable() {
        return remainingQuantity != null && remainingQuantity > 0
                && (status == OrderStatus.REQUESTED
                || status == OrderStatus.PARTIALLY_FILLED
                || status == OrderStatus.TRIGGER_PENDING);
    }

    public boolean isActive() {
        return remainingQuantity > 0
                && (status == OrderStatus.REQUESTED || status == OrderStatus.PARTIALLY_FILLED);
    }
}
