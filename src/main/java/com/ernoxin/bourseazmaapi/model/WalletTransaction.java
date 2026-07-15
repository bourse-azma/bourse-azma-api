package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal amount; // Positive for increase, negative for decrease

    @Column(nullable = false)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private String description;

    @Column(length = 30)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_admin_id")
    private User performedByAdmin;

    @Column(length = 1000)
    private String adminNote;

    @Column(nullable = false)
    private Instant createdAt;
}
