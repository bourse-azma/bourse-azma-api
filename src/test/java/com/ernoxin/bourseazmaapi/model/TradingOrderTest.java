package com.ernoxin.bourseazmaapi.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingOrderTest {

    @Test
    void partiallyFilledMarketOrderWithNoRemainderCannotBeCancelled() {
        TradingOrder order = new TradingOrder();
        order.setStatus(OrderStatus.PARTIALLY_FILLED);
        order.setRemainingQuantity(0L);

        assertThat(order.isCancellable()).isFalse();
        assertThat(order.isActive()).isFalse();
    }

    @Test
    void restingPartiallyFilledOrderRemainsCancellable() {
        TradingOrder order = new TradingOrder();
        order.setStatus(OrderStatus.PARTIALLY_FILLED);
        order.setRemainingQuantity(4L);

        assertThat(order.isCancellable()).isTrue();
        assertThat(order.isActive()).isTrue();
    }
}
