package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
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
    private final OrderUpdateNotifier orderUpdateNotifier;

    /**
     * End the simulated trading day atomically: daily orders expire and every user's
     * private consumption overlay is removed so the next session starts from TSETMC.
     */
    @Transactional
    public SessionResetResult closeCurrentSession() {
        Instant now = Instant.now();
        List<TradingOrder> expiringOrders =
                tradingOrderRepository.findAllByStatusInAndRemainingQuantityGreaterThan(
                        DAILY_ACTIVE_STATUSES, 0L);
        int expiredOrders = tradingOrderRepository.expireAllActiveOrders(DAILY_ACTIVE_STATUSES, now);
        publishExpiredOrders(expiringOrders, now);
        int clearedLevels = consumptionRepository.deleteAllForSessionReset();
        return new SessionResetResult(expiredOrders, clearedLevels);
    }

    /**
     * Startup/midnight safety net for a process that missed the open-to-closed transition.
     */
    @Transactional
    public int expireOrdersBefore(Instant cutoff) {
        Instant now = Instant.now();
        List<TradingOrder> expiringOrders =
                tradingOrderRepository.findAllByStatusInAndRemainingQuantityGreaterThanAndOrderTimeBefore(
                        DAILY_ACTIVE_STATUSES, 0L, cutoff);
        int expiredOrders = tradingOrderRepository.expireActiveOrdersBefore(
                DAILY_ACTIVE_STATUSES, cutoff, now);
        publishExpiredOrders(expiringOrders, now);
        return expiredOrders;
    }

    private void publishExpiredOrders(List<TradingOrder> orders, Instant cancelledAt) {
        if (orders == null) {
            return;
        }
        for (TradingOrder order : orders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setRemainingQuantity(0L);
            order.setCancelledAt(cancelledAt);
            orderUpdateNotifier.publish(order);
        }
    }

    public record SessionResetResult(int expiredOrders, int clearedLiquidityLevels) {
    }
}
