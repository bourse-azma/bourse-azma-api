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

    private static final BigDecimal EQUAL_TRIGGER_TOLERANCE = new BigDecimal("0.50");

    private final TradingOrderRepository tradingOrderRepository;
    private final MarketLiquidityService marketLiquidityService;
    private final OrderMatchingService orderMatchingService;
    private final UserRepository userRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;

    @Transactional
    public int evaluatePendingTriggers() {
        List<Long> pendingOrderIds =
                tradingOrderRepository.findIdsByStatusOrderByOrderTimeAsc(OrderStatus.TRIGGER_PENDING);
        int triggeredCount = 0;

        for (Long orderId : pendingOrderIds) {
            Long userId = tradingOrderRepository.findUserIdByOrderId(orderId).orElse(null);
            if (userId == null) {
                continue;
            }
            User user = userRepository.findByIdForUpdate(userId).orElse(null);
            if (user == null) {
                continue;
            }
            TradingOrder order = tradingOrderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.TRIGGER_PENDING
                    || order.getUser() == null || !userId.equals(order.getUser().getId())
                    || order.getRemainingQuantity() == null || order.getRemainingQuantity() <= 0) {
                continue;
            }
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

            if (order.getPriceType() == PriceType.MARKET) {
                BigDecimal executionPrice = marketLiquidityService.resolveMarketOrderPrice(
                        order.getInstrumentCode(), order.getSide());
                order.setOrderPrice(executionPrice);
                order.setLivePrice(executionPrice);
            }

            if (!canExecuteTriggeredOrder(order, user)) {
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

    private boolean canExecuteTriggeredOrder(TradingOrder order, User user) {
        if (order.getSide() == OrderSide.BUY) {
            return hasBuyingPowerForTrigger(order, user);
        }
        return hasSellableQuantityForTrigger(order, user);
    }

    private boolean hasBuyingPowerForTrigger(TradingOrder order, User user) {
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal committed = tradingOrderRepository.sumReservedBuyValueExcluding(user.getId(), order.getId());
        BigDecimal buyingPower = balance.subtract(committed).max(BigDecimal.ZERO);
        BigDecimal orderValue = order.getOrderPrice().multiply(BigDecimal.valueOf(order.getRemainingQuantity()));
        return orderValue.compareTo(buyingPower) <= 0;
    }

    private boolean hasSellableQuantityForTrigger(TradingOrder order, User user) {
        long held = portfolioHoldingRepository.findAllByUserIdAndInstrumentCodeForUpdate(
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
            case EQUAL -> referencePrice.subtract(triggerPrice).abs()
                    .compareTo(EQUAL_TRIGGER_TOLERANCE) <= 0;
        };
    }
}
