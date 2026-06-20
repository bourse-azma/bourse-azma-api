package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "watchlist_symbols",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_watchlist_symbol_key", columnNames = {"watchlist_id", "symbol_key"})
        }
)
public class WatchlistSymbol extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @Column(nullable = false, length = 120)
    private String symbolKey;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 20)
    private String sourceType;

    @Column(length = 60)
    private String instrumentCode;

    @Column(length = 40)
    private String isin;
}
