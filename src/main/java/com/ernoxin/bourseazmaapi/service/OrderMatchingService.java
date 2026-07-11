package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.PriceType;
import com.ernoxin.bourseazmaapi.model.Trade;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.PrivateBookMatcher;
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
    private final PrivateBookMatcher privateBookMatcher;

    /**
     * Match the given incoming order inside the user's private book:
     * own opposite resting orders first, then residual public market depth.
     * Application users are never counterparties to one another.
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

        List<Trade> trades = new ArrayList<>(privateBookMatcher.matchFully(lockedOrder));
        closeUnfilledMarketOrder(lockedOrder);
        return trades;
    }

    /**
     * Re-run matching for a single user's isolated book after a public market update,
     * cancel, or other liquidity change. Uses price-time priority.
     */
    @Transactional
    public List<Trade> runMatchingForUserInstrument(Long userId, String instrumentCode) {
        List<Trade> allTrades = new ArrayList<>();
        allTrades.addAll(privateBookMatcher.collapseSelfCrosses(userId, instrumentCode));

        // Buys: best price first, then earliest time
        for (Long orderId : tradingOrderRepository.findActiveBuyOrderIdsForMatching(
                userId, instrumentCode, ACTIVE_STATUSES)) {
            TradingOrder order = tradingOrderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order != null && order.isActive()) {
                allTrades.addAll(privateBookMatcher.matchAgainstResidualMarket(order));
                closeUnfilledMarketOrder(order);
            }
        }

        // Sells: best price first, then earliest time
        for (Long orderId : tradingOrderRepository.findActiveSellOrderIdsForMatching(
                userId, instrumentCode, ACTIVE_STATUSES)) {
            TradingOrder order = tradingOrderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order != null && order.isActive()) {
                allTrades.addAll(privateBookMatcher.matchAgainstResidualMarket(order));
                closeUnfilledMarketOrder(order);
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
