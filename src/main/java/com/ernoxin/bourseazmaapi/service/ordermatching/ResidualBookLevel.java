package com.ernoxin.bourseazmaapi.service.ordermatching;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        if (residualVolume <= 0 || publicOrderCount <= 0 || publicVolume <= 0) {
            return 0L;
        }
        if (residualVolume >= publicVolume) {
            return publicOrderCount;
        }
        // The public feed exposes only aggregate volume/count, not each individual order.
        // Scale the displayed count with the remaining volume instead of leaving an obviously
        // stale full count after a private fill. Ceiling keeps a non-empty level non-empty.
        return BigDecimal.valueOf(publicOrderCount)
                .multiply(BigDecimal.valueOf(residualVolume))
                .divide(BigDecimal.valueOf(publicVolume), 0, RoundingMode.CEILING)
                .longValueExact();
    }

    public long take(long requested) {
        long taken = Math.min(Math.max(0L, requested), residualVolume);
        residualVolume -= taken;
        return taken;
    }
}
