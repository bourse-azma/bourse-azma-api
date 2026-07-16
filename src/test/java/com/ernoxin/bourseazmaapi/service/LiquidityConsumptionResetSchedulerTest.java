package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LiquidityConsumptionResetSchedulerTest {

    @Test
    void deletesOnlyConsumptionsFromBeforeTheCurrentTehranDay() {
        UserLiquidityConsumptionRepository repository = mock(UserLiquidityConsumptionRepository.class);
        ZoneId tehran = ZoneId.of("Asia/Tehran");
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T08:30:00Z"), tehran);
        LiquidityConsumptionResetScheduler scheduler =
                new LiquidityConsumptionResetScheduler(repository, clock);

        scheduler.resetPreviousDays();

        verify(repository).deleteAllUpdatedBefore(Instant.parse("2026-07-15T20:30:00Z"));
    }
}
