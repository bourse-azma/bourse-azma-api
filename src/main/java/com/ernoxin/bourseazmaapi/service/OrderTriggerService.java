package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.model.TriggerComparator;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTriggerService {

    private final TradingOrderRepository tradingOrderRepository;
    private final MarketLiquidityService marketLiquidityService;
    private final OrderMatchingService orderMatchingService;

    @Transactional
    public int evaluatePendingTriggers() {
        List<TradingOrder> pendingOrders =
                tradingOrderRepository.findAllByStatusOrderByOrderTimeAsc(OrderStatus.TRIGGER_PENDING);
        int triggeredCount = 0;

        for (TradingOrder order : pendingOrders) {
            if (order.getTriggerComparator() == null || order.getTriggerPrice() == null) {
                continue;
            }

            BigDecimal referencePrice = marketLiquidityService
                    .getReferencePrice(order.getInstrumentCode())
                    .orElse(order.getLivePrice());
            if (referencePrice == null) {
                continue;
            }

            referencePrice = referencePrice.setScale(2, RoundingMode.HALF_UP);
            order.setLivePrice(referencePrice);

            if (!isTriggerMet(referencePrice, order.getTriggerComparator(), order.getTriggerPrice())) {
                tradingOrderRepository.save(order);
                continue;
            }

            order.setStatus(OrderStatus.REQUESTED);
            tradingOrderRepository.save(order);
            orderMatchingService.matchOrder(order);
            triggeredCount++;
        }

        return triggeredCount;
    }

    private boolean isTriggerMet(BigDecimal referencePrice, TriggerComparator comparator, BigDecimal triggerPrice) {
        int comparison = referencePrice.compareTo(triggerPrice);
        return switch (comparator) {
            case GREATER_THAN -> comparison > 0;
            case LESS_THAN -> comparison < 0;
            case EQUAL -> comparison == 0;
        };
    }
}
