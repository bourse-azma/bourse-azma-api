package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTriggerService {

    private final TradingOrderRepository tradingOrderRepository;
    private final MarketLiquidityService marketLiquidityService;
    private final OrderMatchingService orderMatchingService;
    private final UserRepository userRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;

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

            if (!canExecuteTriggeredOrder(order)) {
                failTriggeredOrder(order, "منابع کافی برای اجرای سفارش شرطی در زمان فعال‌سازی وجود ندارد.");
                continue;
            }

            order.setStatus(OrderStatus.REQUESTED);
            tradingOrderRepository.save(order);
            orderMatchingService.matchOrder(order);
            triggeredCount++;
        }

        return triggeredCount;
    }

    private boolean canExecuteTriggeredOrder(TradingOrder order) {
        if (order.getSide() == OrderSide.BUY) {
            return hasBuyingPowerForTrigger(order);
        }
        return hasSellableQuantityForTrigger(order);
    }

    private boolean hasBuyingPowerForTrigger(TradingOrder order) {
        User user = userRepository.findByIdForUpdate(order.getUser().getId()).orElse(null);
        if (user == null) {
            return false;
        }
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal committed = tradingOrderRepository.sumReservedBuyValueExcluding(user.getId(), order.getId());
        BigDecimal buyingPower = balance.subtract(committed).max(BigDecimal.ZERO);
        BigDecimal orderValue = order.getOrderPrice().multiply(BigDecimal.valueOf(order.getRemainingQuantity()));
        return orderValue.compareTo(buyingPower) <= 0;
    }

    private boolean hasSellableQuantityForTrigger(TradingOrder order) {
        User user = userRepository.findByIdForUpdate(order.getUser().getId()).orElse(null);
        if (user == null) {
            return false;
        }
        long held = portfolioHoldingRepository.findAllByUserIdAndInstrumentCode(
                        user.getId(), order.getInstrumentCode())
                .stream()
                .mapToLong(holding -> holding.getQuantity())
                .sum();
        long reserved = tradingOrderRepository.sumReservedSellQuantityExcluding(
                user.getId(), order.getInstrumentCode(), order.getId());
        long available = held - reserved;
        return order.getRemainingQuantity() <= available;
    }

    private void failTriggeredOrder(TradingOrder order, String reason) {
        order.setStatus(OrderStatus.FAILED);
        order.setRemainingQuantity(0L);
        order.setCancelledAt(Instant.now());
        tradingOrderRepository.save(order);
        log.warn("Conditional order {} failed on trigger: {}", order.getId(), reason);
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
