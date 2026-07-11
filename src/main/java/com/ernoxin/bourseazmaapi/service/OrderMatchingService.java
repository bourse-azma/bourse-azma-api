package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.PriceType;
import com.ernoxin.bourseazmaapi.model.Trade;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.MarketOrderMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED);

    private final TradingOrderRepository tradingOrderRepository;
    private final MarketOrderMatcher marketOrderMatcher;

    /**
     * Match the given incoming order against the public order-book snapshot assigned to its
     * private simulation. Application users are never counterparties to one another.
     * The caller must have already saved the order with REQUESTED status.
     */
    @Transactional
    public List<Trade> matchOrder(TradingOrder incomingOrder) {
        if (incomingOrder == null || incomingOrder.getId() == null) {
            return List.of();
        }
        TradingOrder lockedOrder = tradingOrderRepository.findByIdForUpdate(incomingOrder.getId())
                .orElse(incomingOrder);
        if (!lockedOrder.isActive()) {
            return List.of();
        }

        List<Trade> trades;
        if (lockedOrder.getSide() == com.ernoxin.bourseazmaapi.model.OrderSide.BUY) {
            trades = new ArrayList<>(marketOrderMatcher.matchBuyAgainstMarket(lockedOrder));
        } else {
            trades = new ArrayList<>(marketOrderMatcher.matchSellAgainstMarket(lockedOrder));
        }
        closeUnfilledMarketOrder(lockedOrder);
        return trades;
    }

    /**
     * Re-run matching for a single user's isolated book after a public market update.
     */
    @Transactional
    public List<Trade> runMatchingForUserInstrument(Long userId, String instrumentCode) {
        List<Trade> allTrades = new ArrayList<>();
        List<Long> orderIds = tradingOrderRepository.findActiveOrderIdsForPrivateBook(
                userId, instrumentCode, ACTIVE_STATUSES);
        for (Long orderId : orderIds) {
            TradingOrder order = tradingOrderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order != null && order.isActive()) {
                allTrades.addAll(matchOrder(order));
            }
        }
        return allTrades;
    }

    @Transactional
    public List<Trade> runMatchingForAllActiveInstruments() {
        List<Trade> allTrades = new ArrayList<>();
        for (String instrumentCode : tradingOrderRepository.findDistinctInstrumentCodesWithActiveOrders()) {
            for (Long userId : tradingOrderRepository.findDistinctUserIdsWithActiveOrders(instrumentCode)) {
                allTrades.addAll(runMatchingForUserInstrument(userId, instrumentCode));
            }
        }
        return allTrades;
    }

    private void closeUnfilledMarketOrder(TradingOrder order) {
        if (order.getPriceType() != PriceType.MARKET || order.getRemainingQuantity() <= 0
                || order.getStatus() == OrderStatus.COMPLETED) {
            return;
        }
        order.setRemainingQuantity(0L);
        order.setCancelledAt(Instant.now());
        order.setStatus(order.getExecutedQuantity() > 0
                ? OrderStatus.PARTIALLY_FILLED
                : OrderStatus.FAILED);
        tradingOrderRepository.save(order);
    }
}
