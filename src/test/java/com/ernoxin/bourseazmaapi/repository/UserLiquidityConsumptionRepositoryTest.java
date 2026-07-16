package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.BookSide;
import com.ernoxin.bourseazmaapi.model.UserLiquidityConsumption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class UserLiquidityConsumptionRepositoryTest {

    @Autowired
    private UserLiquidityConsumptionRepository repository;

    @Test
    void deleteAllUpdatedBeforeKeepsRowsFromTheCurrentDay() {
        repository.save(consumption("100.00", "2026-07-15T19:00:00Z"));
        UserLiquidityConsumption current = repository.save(
                consumption("200.00", "2026-07-15T21:00:00Z"));

        int deleted = repository.deleteAllUpdatedBefore(Instant.parse("2026-07-15T20:30:00Z"));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll())
                .singleElement()
                .extracting(UserLiquidityConsumption::getId)
                .isEqualTo(current.getId());
    }

    private UserLiquidityConsumption consumption(String price, String updatedAt) {
        UserLiquidityConsumption row = new UserLiquidityConsumption();
        row.setUserId(1L);
        row.setInstrumentCode("IRO1TEST0001");
        row.setBookSide(BookSide.ASK);
        row.setPrice(new BigDecimal(price));
        row.setConsumedQuantity(10L);
        row.setUpdatedAt(Instant.parse(updatedAt));
        return row;
    }
}
