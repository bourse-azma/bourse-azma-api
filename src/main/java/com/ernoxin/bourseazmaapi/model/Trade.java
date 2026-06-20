package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "trades")
public class Trade extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buy_order_id", nullable = false)
    private TradingOrder buyOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sell_order_id", nullable = false)
    private TradingOrder sellOrder;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 60)
    private String instrumentCode;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal price;

    @Column(name = "trade_value", nullable = false, precision = 38, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private Instant executedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;
}
