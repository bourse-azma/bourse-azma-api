package com.ernoxin.bourseazmaapi.service.ordermatching;

import java.math.BigDecimal;

/**
 * One price level of residual public liquidity inside a user's private simulation.
 */
public final class ResidualBookLevel {
    private final BigDecimal price;
    private final long publicVolume;
    private final long publicOrderCount;
    private long residualVolume;

    public ResidualBookLevel(BigDecimal price, long publicVolume, long publicOrderCount, long residualVolume) {
        this.price = price;
        this.publicVolume = publicVolume;
        this.publicOrderCount = publicOrderCount;
        this.residualVolume = Math.max(0L, residualVolume);
    }

    public BigDecimal price() {
        return price;
    }

    public long publicVolume() {
        return publicVolume;
    }

    public long publicOrderCount() {
        return publicOrderCount;
    }

    public long residualVolume() {
        return residualVolume;
    }

    public long displayOrderCount() {
        if (residualVolume <= 0) {
            return 0L;
        }
        if (residualVolume >= publicVolume) {
            return publicOrderCount;
        }
        return Math.max(1L, publicOrderCount);
    }

    public long take(long requested) {
        long taken = Math.min(Math.max(0L, requested), residualVolume);
        residualVolume -= taken;
        return taken;
    }
}
