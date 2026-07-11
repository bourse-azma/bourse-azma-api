package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tracks how much public market depth a single user has already taken in their private
 * simulation, so residual liquidity cannot be infinite against a static TSETMC snapshot.
 */
@Getter
@Setter
@Entity
@Table(name = "user_liquidity_consumptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_liquidity_level",
                columnNames = {"user_id", "instrument_code", "book_side", "price"}
        ),
        indexes = {
                @Index(name = "idx_user_liquidity_lookup",
                        columnList = "user_id,instrument_code,book_side")
        })
public class UserLiquidityConsumption extends BaseEntity<Long> {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_code", nullable = false, length = 60)
    private String instrumentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "book_side", nullable = false, length = 8)
    private BookSide bookSide;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Long consumedQuantity;

    @Column(nullable = false)
    private Instant updatedAt;
}
