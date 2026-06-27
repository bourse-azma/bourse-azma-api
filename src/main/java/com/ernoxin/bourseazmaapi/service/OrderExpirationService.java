package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationService {

    private static final List<OrderStatus> EXPIRABLE_STATUSES = List.of(
            OrderStatus.REQUESTED,
            OrderStatus.PARTIALLY_FILLED,
            OrderStatus.TRIGGER_PENDING
    );

    private final TradingOrderRepository tradingOrderRepository;
    private final OrderMatchingService orderMatchingService;

    @Transactional
    public int cancelExpiredOrders() {
        Instant now = Instant.now();
        List<TradingOrder> expiredOrders =
                tradingOrderRepository.findAllByStatusInAndExpiresAtBefore(EXPIRABLE_STATUSES, now);
        if (expiredOrders.isEmpty()) {
            return 0;
        }

        for (TradingOrder order : expiredOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(now);
            order.setRemainingQuantity(0L);
            tradingOrderRepository.save(order);
            orderMatchingService.runMatchingForInstrument(order.getInstrumentCode());
        }

        log.debug("Cancelled {} expired order(s).", expiredOrders.size());
        return expiredOrders.size();
    }
}
