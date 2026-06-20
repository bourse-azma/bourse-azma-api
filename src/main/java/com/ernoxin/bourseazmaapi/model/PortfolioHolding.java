package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "portfolio_holdings")
public class PortfolioHolding extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 60)
    private String instrumentCode;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal buyPrice;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal livePrice;

    @Column(nullable = false)
    private Instant acquiredAt;
}
