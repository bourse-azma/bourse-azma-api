package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.PrivateBookStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches a user's order inside their private simulation:
 * 1) against their own opposite resting orders (price-time priority, maker price)
 * 2) against residual public market depth assigned to that user
 */
@Component
@RequiredArgsConstructor
public class PrivateBookMatcher {

    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED);

    private final TradingOrderRepository tradingOrderRepository;
    private final PrivateBookStateService privateBookStateService;
    private final MarketMakerService marketMakerService;
    private final TradeExecutor tradeExecutor;

    public List<Trade> matchFully(TradingOrder order) {
        List<Trade> trades = new ArrayList<>();
        trades.addAll(matchAgainstOwnBook(order));
        if (order.getRemainingQuantity() > 0 && order.isActive()) {
            trades.addAll(matchAgainstResidualMarket(order));
        }
        return trades;
    }

    public List<Trade> matchAgainstOwnBook(TradingOrder order) {
        if (order == null || order.getUser() == null || !order.isActive()) {
            return List.of();
        }
        Long userId = order.getUser().getId();
        String instrumentCode = order.getInstrumentCode();
        List<Trade> trades = new ArrayList<>();

        if (order.getSide() == OrderSide.BUY) {
            List<TradingOrder> sells = tradingOrderRepository.findOwnRestingSellsForMatch(
                    userId, instrumentCode, order.getOrderPrice(), ACTIVE_STATUSES, order.getId());
            for (TradingOrder sell : sells) {
                if (!order.isActive()) {
                    break;
                }
                TradingOrder lockedSell = tradingOrderRepository.findByIdForUpdate(sell.getId()).orElse(null);
                if (lockedSell == null || !lockedSell.isActive() || lockedSell.getSide() != OrderSide.SELL) {
                    continue;
                }
                if (order.getOrderPrice().compareTo(lockedSell.getOrderPrice()) < 0) {
                    break;
                }
                long qty = Math.min(order.getRemainingQuantity(), lockedSell.getRemainingQuantity());
                if (qty <= 0) {
                    continue;
                }
                // Maker (resting sell) price
                BigDecimal price = lockedSell.getOrderPrice();
                Trade trade = tradeExecutor.executeTrade(order, lockedSell, qty, price);
                if (trade != null) {
                    trades.add(trade);
                }
            }
        } else {
            List<TradingOrder> buys = tradingOrderRepository.findOwnRestingBuysForMatch(
                    userId, instrumentCode, order.getOrderPrice(), ACTIVE_STATUSES, order.getId());
            for (TradingOrder buy : buys) {
                if (!order.isActive()) {
                    break;
                }
                TradingOrder lockedBuy = tradingOrderRepository.findByIdForUpdate(buy.getId()).orElse(null);
                if (lockedBuy == null || !lockedBuy.isActive() || lockedBuy.getSide() != OrderSide.BUY) {
                    continue;
                }
                if (order.getOrderPrice().compareTo(lockedBuy.getOrderPrice()) > 0) {
                    break;
                }
                long qty = Math.min(order.getRemainingQuantity(), lockedBuy.getRemainingQuantity());
                if (qty <= 0) {
                    continue;
                }
                // Maker (resting buy) price
                BigDecimal price = lockedBuy.getOrderPrice();
                Trade trade = tradeExecutor.executeTrade(lockedBuy, order, qty, price);
                if (trade != null) {
                    trades.add(trade);
                }
            }
        }
        return trades;
    }

    public List<Trade> matchAgainstResidualMarket(TradingOrder order) {
        if (order == null || order.getUser() == null || !order.isActive()) {
            return List.of();
        }
        Long userId = order.getUser().getId();
        String instrumentCode = order.getInstrumentCode();
        List<Trade> trades = new ArrayList<>();

        if (order.getSide() == OrderSide.BUY) {
            List<ResidualBookLevel> asks = privateBookStateService.loadResidualAskLevels(userId, instrumentCode);
            for (ResidualBookLevel level : asks) {
                if (!order.isActive()) {
                    break;
                }
                if (order.getOrderPrice().compareTo(level.price()) < 0) {
                    break;
                }
                long matchQuantity = level.take(order.getRemainingQuantity());
                if (matchQuantity <= 0) {
                    continue;
                }
                TradingOrder marketSell = marketMakerService.createCompletedCounterOrder(
                        order, OrderSide.SELL, matchQuantity, level.price());
                Trade trade = tradeExecutor.executeTrade(order, marketSell, matchQuantity, level.price());
                if (trade != null) {
                    privateBookStateService.consume(userId, instrumentCode, BookSide.ASK, level.price(), matchQuantity);
                    trades.add(trade);
                } else {
                    marketMakerService.cancelUnmatchedCounterOrder(marketSell);
                    // Put residual back for this in-memory level; DB consumption was not recorded.
                    break;
                }
            }
        } else {
            List<ResidualBookLevel> bids = privateBookStateService.loadResidualBidLevels(userId, instrumentCode);
            for (ResidualBookLevel level : bids) {
                if (!order.isActive()) {
                    break;
                }
                if (order.getOrderPrice().compareTo(level.price()) > 0) {
                    break;
                }
                long matchQuantity = level.take(order.getRemainingQuantity());
                if (matchQuantity <= 0) {
                    continue;
                }
                TradingOrder marketBuy = marketMakerService.createCompletedCounterOrder(
                        order, OrderSide.BUY, matchQuantity, level.price());
                Trade trade = tradeExecutor.executeTrade(marketBuy, order, matchQuantity, level.price());
                if (trade != null) {
                    privateBookStateService.consume(userId, instrumentCode, BookSide.BID, level.price(), matchQuantity);
                    trades.add(trade);
                } else {
                    marketMakerService.cancelUnmatchedCounterOrder(marketBuy);
                    break;
                }
            }
        }
        return trades;
    }

    /**
     * Continuously matches the user's best bid against best ask while they cross.
     */
    public List<Trade> collapseSelfCrosses(Long userId, String instrumentCode) {
        List<Trade> trades = new ArrayList<>();
        while (true) {
            List<TradingOrder> buys = tradingOrderRepository
                    .findActiveBuysPriceTime(userId, instrumentCode, ACTIVE_STATUSES);
            List<TradingOrder> sells = tradingOrderRepository
                    .findActiveSellsPriceTime(userId, instrumentCode, ACTIVE_STATUSES);
            if (buys.isEmpty() || sells.isEmpty()) {
                break;
            }
            TradingOrder bestBuy = buys.getFirst();
            TradingOrder bestSell = sells.getFirst();
            if (bestBuy.getOrderPrice().compareTo(bestSell.getOrderPrice()) < 0) {
                break;
            }
            TradingOrder lockedBuy = tradingOrderRepository.findByIdForUpdate(bestBuy.getId()).orElse(null);
            TradingOrder lockedSell = tradingOrderRepository.findByIdForUpdate(bestSell.getId()).orElse(null);
            if (lockedBuy == null || lockedSell == null || !lockedBuy.isActive() || !lockedSell.isActive()) {
                break;
            }
            if (lockedBuy.getOrderPrice().compareTo(lockedSell.getOrderPrice()) < 0) {
                break;
            }
            long qty = Math.min(lockedBuy.getRemainingQuantity(), lockedSell.getRemainingQuantity());
            if (qty <= 0) {
                break;
            }
            // Older resting order is maker when both already rest; prefer earlier order time.
            BigDecimal price = lockedBuy.getOrderTime().isBefore(lockedSell.getOrderTime())
                    || lockedBuy.getOrderTime().equals(lockedSell.getOrderTime())
                    ? lockedBuy.getOrderPrice()
                    : lockedSell.getOrderPrice();
            // Clamp to valid range between sell and buy
            if (price.compareTo(lockedSell.getOrderPrice()) < 0) {
                price = lockedSell.getOrderPrice();
            }
            if (price.compareTo(lockedBuy.getOrderPrice()) > 0) {
                price = lockedBuy.getOrderPrice();
            }
            Trade trade = tradeExecutor.executeTrade(lockedBuy, lockedSell, qty, price);
            if (trade == null) {
                break;
            }
            trades.add(trade);
        }
        return trades;
    }
}
