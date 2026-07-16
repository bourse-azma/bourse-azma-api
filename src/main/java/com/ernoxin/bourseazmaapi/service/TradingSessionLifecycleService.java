package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingSessionLifecycleService {

    private static final List<OrderStatus> DAILY_ACTIVE_STATUSES = List.of(
            OrderStatus.REQUESTED,
            OrderStatus.PARTIALLY_FILLED,
            OrderStatus.TRIGGER_PENDING
    );

    private final TradingOrderRepository tradingOrderRepository;
    private final UserLiquidityConsumptionRepository consumptionRepository;

    /**
     * End the simulated trading day atomically: daily orders expire and every user's
     * private consumption overlay is removed so the next session starts from TSETMC.
     */
    @Transactional
    public SessionResetResult closeCurrentSession() {
        Instant now = Instant.now();
        int expiredOrders = tradingOrderRepository.expireAllActiveOrders(DAILY_ACTIVE_STATUSES, now);
        int clearedLevels = consumptionRepository.deleteAllForSessionReset();
        return new SessionResetResult(expiredOrders, clearedLevels);
    }

    /**
     * Startup/midnight safety net for a process that missed the open-to-closed transition.
     */
    @Transactional
    public int expireOrdersBefore(Instant cutoff) {
        return tradingOrderRepository.expireActiveOrdersBefore(
                DAILY_ACTIVE_STATUSES, cutoff, Instant.now());
    }

    public record SessionResetResult(int expiredOrders, int clearedLiquidityLevels) {
    }
}
