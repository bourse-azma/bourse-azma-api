package com.ernoxin.bourseazmaapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.trading")
public record TradingRulesProperties(
        BigDecimal minimumOrderValue,
        BigDecimal maximumWalletAdjustment
) {
    private static final BigDecimal DEFAULT_MINIMUM_ORDER_VALUE = new BigDecimal("5000000");
    private static final BigDecimal DEFAULT_MAXIMUM_WALLET_ADJUSTMENT = new BigDecimal("1000000000000");

    public TradingRulesProperties {
        if (minimumOrderValue == null || minimumOrderValue.signum() <= 0) {
            minimumOrderValue = DEFAULT_MINIMUM_ORDER_VALUE;
        }
        if (maximumWalletAdjustment == null || maximumWalletAdjustment.signum() <= 0) {
            maximumWalletAdjustment = DEFAULT_MAXIMUM_WALLET_ADJUSTMENT;
        }
    }
}
