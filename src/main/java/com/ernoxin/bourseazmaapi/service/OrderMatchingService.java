package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.Trade;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.MarketOrderMatcher;
import com.ernoxin.bourseazmaapi.service.ordermatching.PeerOrderMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED);

    private final TradingOrderRepository tradingOrderRepository;
    private final PeerOrderMatcher peerOrderMatcher;
    private final MarketOrderMatcher marketOrderMatcher;

    /**
     * Match the given incoming order against peer orders and TSETMC order-book liquidity.
     * The caller must have already saved the order with REQUESTED status.
     */
    @Transactional
    public List<Trade> matchOrder(TradingOrder incomingOrder) {
        List<Trade> trades = new ArrayList<>();
        if (incomingOrder.getSide() == OrderSide.BUY) {
            trades.addAll(peerOrderMatcher.matchBuyOrder(incomingOrder, ACTIVE_STATUSES));
            if (incomingOrder.getRemainingQuantity() > 0) {
                trades.addAll(marketOrderMatcher.matchBuyAgainstMarket(incomingOrder));
            }
        } else {
            trades.addAll(peerOrderMatcher.matchSellOrder(incomingOrder, ACTIVE_STATUSES));
            if (incomingOrder.getRemainingQuantity() > 0) {
                trades.addAll(marketOrderMatcher.matchSellAgainstMarket(incomingOrder));
            }
        }
        return trades;
    }

    /**
     * Re-run matching for all active orders on a given instrument.
     * Useful after cancellation frees liquidity or after TSETMC order-book updates.
     */
    @Transactional
    public List<Trade> runMatchingForInstrument(String instrumentCode) {
        List<Trade> allTrades = new ArrayList<>();

        List<TradingOrder> activeBuys = tradingOrderRepository.findActiveBuyOrders(
                instrumentCode, OrderSide.BUY, ACTIVE_STATUSES);
        for (TradingOrder buyOrder : activeBuys) {
            if (buyOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            allTrades.addAll(peerOrderMatcher.matchBuyOrder(buyOrder, ACTIVE_STATUSES));
            if (buyOrder.getRemainingQuantity() > 0) {
                allTrades.addAll(marketOrderMatcher.matchBuyAgainstMarket(buyOrder));
            }
        }

        List<TradingOrder> activeSells = tradingOrderRepository.findActiveSellOrders(
                instrumentCode, OrderSide.SELL, ACTIVE_STATUSES);
        for (TradingOrder sellOrder : activeSells) {
            if (sellOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            allTrades.addAll(peerOrderMatcher.matchSellOrder(sellOrder, ACTIVE_STATUSES));
            if (sellOrder.getRemainingQuantity() > 0) {
                allTrades.addAll(marketOrderMatcher.matchSellAgainstMarket(sellOrder));
            }
        }

        return allTrades;
    }

    @Transactional
    public List<Trade> runMatchingForAllActiveInstruments() {
        List<Trade> allTrades = new ArrayList<>();
        for (String instrumentCode : tradingOrderRepository.findDistinctInstrumentCodesWithActiveOrders()) {
            allTrades.addAll(runMatchingForInstrument(instrumentCode));
        }
        return allTrades;
    }
}
