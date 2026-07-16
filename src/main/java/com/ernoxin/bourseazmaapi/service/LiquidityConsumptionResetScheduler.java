package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Component
@Slf4j
public class LiquidityConsumptionResetScheduler {

    private final UserLiquidityConsumptionRepository consumptionRepository;
    private final Clock clock;

    @Autowired
    public LiquidityConsumptionResetScheduler(
            UserLiquidityConsumptionRepository consumptionRepository,
            @Value("${app.liquidity-consumption.reset-zone:Asia/Tehran}") String resetZone) {
        this(consumptionRepository, Clock.system(ZoneId.of(resetZone)));
    }

    LiquidityConsumptionResetScheduler(
            UserLiquidityConsumptionRepository consumptionRepository,
            Clock clock) {
        this.consumptionRepository = consumptionRepository;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${app.liquidity-consumption.reset-cron:0 0 0 * * *}",
            zone = "${app.liquidity-consumption.reset-zone:Asia/Tehran}")
    public void resetPreviousDays() {
        Instant startOfToday = clock.instant()
                .atZone(clock.getZone())
                .toLocalDate()
                .atStartOfDay(clock.getZone())
                .toInstant();
        int deleted = consumptionRepository.deleteAllUpdatedBefore(startOfToday);
        if (deleted > 0) {
            log.info("Cleared {} stale user liquidity consumption records", deleted);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resetPreviousDaysOnStartup() {
        resetPreviousDays();
    }
}
