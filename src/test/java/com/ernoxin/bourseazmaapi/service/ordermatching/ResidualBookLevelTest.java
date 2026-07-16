package com.ernoxin.bourseazmaapi.service.ordermatching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ResidualBookLevelTest {

    @Test
    void scalesDisplayedOrderCountAfterPartialConsumption() {
        ResidualBookLevel level = new ResidualBookLevel(new BigDecimal("100"), 1_000, 10, 1_000);

        assertThat(level.displayOrderCount()).isEqualTo(10);
        assertThat(level.take(550)).isEqualTo(550);
        assertThat(level.residualVolume()).isEqualTo(450);
        assertThat(level.displayOrderCount()).isEqualTo(5);
        assertThat(level.take(450)).isEqualTo(450);
        assertThat(level.displayOrderCount()).isZero();
    }
}
