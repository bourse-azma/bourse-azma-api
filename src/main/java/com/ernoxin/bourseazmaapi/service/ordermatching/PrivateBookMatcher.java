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
 * Matches a user's order against one isolated combined book: the user's own resting
 * orders plus the residual public depth assigned to that user, all under price-time rules.
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
        if (order == null || order.getUser() == null || !order.isActive()) {
            return List.of();
        }

        return order.getSide() == OrderSide.BUY
                ? matchBuyByBestPrice(order)
                : matchSellByBestPrice(order);
    }

    /**
     * Match an incoming buy against one combined private ask book. Public levels and the
     * user's own resting sells compete by price; public liquidity wins ties because it was
     * already present in the market snapshot before the incoming order arrived.
     */
    private List<Trade> matchBuyByBestPrice(TradingOrder order) {
        Long userId = order.getUser().getId();
        String instrumentCode = order.getInstrumentCode();
        List<TradingOrder> ownSells = tradingOrderRepository.findOwnRestingSellsForMatch(
                userId, instrumentCode, order.getOrderPrice(), ACTIVE_STATUSES, order.getId());
        List<ResidualBookLevel> publicAsks = privateBookStateService.loadResidualAskLevels(userId, instrumentCode);
        List<Trade> trades = new ArrayList<>();

        int ownIndex = 0;
        int publicIndex = 0;
        TradingOrder lockedOwnSell = null;
        while (order.isActive()) {
            while (lockedOwnSell == null && ownIndex < ownSells.size()) {
                TradingOrder candidate = ownSells.get(ownIndex);
                TradingOrder locked = tradingOrderRepository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (locked != null && locked.isActive() && locked.getSide() == OrderSide.SELL
                        && order.getOrderPrice().compareTo(locked.getOrderPrice()) >= 0) {
                    lockedOwnSell = locked;
                } else {
                    ownIndex++;
                }
            }
            while (publicIndex < publicAsks.size()
                    && publicAsks.get(publicIndex).residualVolume() <= 0) {
                publicIndex++;
            }

            ResidualBookLevel publicAsk = publicIndex < publicAsks.size()
                    && order.getOrderPrice().compareTo(publicAsks.get(publicIndex).price()) >= 0
                    ? publicAsks.get(publicIndex)
                    : null;
            if (lockedOwnSell == null && publicAsk == null) {
                break;
            }

            boolean usePublic = publicAsk != null && (lockedOwnSell == null
                    || publicAsk.price().compareTo(lockedOwnSell.getOrderPrice()) <= 0);
            if (usePublic) {
                if (!executeAgainstPublicLevel(
                        order, OrderSide.SELL, BookSide.ASK, publicAsk, trades)) {
                    break;
                }
                if (publicAsk.residualVolume() <= 0) {
                    publicIndex++;
                }
            } else {
                long quantity = Math.min(order.getRemainingQuantity(), lockedOwnSell.getRemainingQuantity());
                BigDecimal executionPrice = ownCrossExecutionPrice(order, lockedOwnSell);
                Trade trade = tradeExecutor.executeTrade(
                        order, lockedOwnSell, quantity, executionPrice);
                if (trade != null) {
                    trades.add(trade);
                }
                if (trade == null || !lockedOwnSell.isActive()) {
                    ownIndex++;
                    lockedOwnSell = null;
                }
            }
        }
        return trades;
    }

    /**
     * Same combined-book rule as {@link #matchBuyByBestPrice(TradingOrder)}, for sells.
     */
    private List<Trade> matchSellByBestPrice(TradingOrder order) {
        Long userId = order.getUser().getId();
        String instrumentCode = order.getInstrumentCode();
        List<TradingOrder> ownBuys = tradingOrderRepository.findOwnRestingBuysForMatch(
                userId, instrumentCode, order.getOrderPrice(), ACTIVE_STATUSES, order.getId());
        List<ResidualBookLevel> publicBids = privateBookStateService.loadResidualBidLevels(userId, instrumentCode);
        List<Trade> trades = new ArrayList<>();

        int ownIndex = 0;
        int publicIndex = 0;
        TradingOrder lockedOwnBuy = null;
        while (order.isActive()) {
            while (lockedOwnBuy == null && ownIndex < ownBuys.size()) {
                TradingOrder candidate = ownBuys.get(ownIndex);
                TradingOrder locked = tradingOrderRepository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (locked != null && locked.isActive() && locked.getSide() == OrderSide.BUY
                        && order.getOrderPrice().compareTo(locked.getOrderPrice()) <= 0) {
                    lockedOwnBuy = locked;
                } else {
                    ownIndex++;
                }
            }
            while (publicIndex < publicBids.size()
                    && publicBids.get(publicIndex).residualVolume() <= 0) {
                publicIndex++;
            }

            ResidualBookLevel publicBid = publicIndex < publicBids.size()
                    && order.getOrderPrice().compareTo(publicBids.get(publicIndex).price()) <= 0
                    ? publicBids.get(publicIndex)
                    : null;
            if (lockedOwnBuy == null && publicBid == null) {
                break;
            }

            boolean usePublic = publicBid != null && (lockedOwnBuy == null
                    || publicBid.price().compareTo(lockedOwnBuy.getOrderPrice()) >= 0);
            if (usePublic) {
                if (!executeAgainstPublicLevel(
                        order, OrderSide.BUY, BookSide.BID, publicBid, trades)) {
                    break;
                }
                if (publicBid.residualVolume() <= 0) {
                    publicIndex++;
                }
            } else {
                long quantity = Math.min(order.getRemainingQuantity(), lockedOwnBuy.getRemainingQuantity());
                BigDecimal executionPrice = ownCrossExecutionPrice(lockedOwnBuy, order);
                Trade trade = tradeExecutor.executeTrade(
                        lockedOwnBuy, order, quantity, executionPrice);
                if (trade != null) {
                    trades.add(trade);
                }
                if (trade == null || !lockedOwnBuy.isActive()) {
                    ownIndex++;
                    lockedOwnBuy = null;
                }
            }
        }
        return trades;
    }

    private boolean executeAgainstPublicLevel(
            TradingOrder userOrder,
            OrderSide counterSide,
            BookSide publicBookSide,
            ResidualBookLevel level,
            List<Trade> trades) {
        long matchQuantity = level.take(userOrder.getRemainingQuantity());
        if (matchQuantity <= 0) {
            return true;
        }
        TradingOrder counterOrder = marketMakerService.createCompletedCounterOrder(
                userOrder, counterSide, matchQuantity, level.price());
        Trade trade = userOrder.getSide() == OrderSide.BUY
                ? tradeExecutor.executeTrade(userOrder, counterOrder, matchQuantity, level.price())
                : tradeExecutor.executeTrade(counterOrder, userOrder, matchQuantity, level.price());
        if (trade == null) {
            marketMakerService.cancelUnmatchedCounterOrder(counterOrder);
            return false;
        }
        privateBookStateService.consume(
                userOrder.getUser().getId(), userOrder.getInstrumentCode(),
                publicBookSide, level.price(), matchQuantity);
        trades.add(trade);
        return true;
    }

    private BigDecimal ownCrossExecutionPrice(TradingOrder buyOrder, TradingOrder sellOrder) {
        BigDecimal price = !buyOrder.getOrderTime().isAfter(sellOrder.getOrderTime())
                ? buyOrder.getOrderPrice()
                : sellOrder.getOrderPrice();
        // Defensive clamp: the execution price of a crossed pair must stay between both limits.
        if (price.compareTo(sellOrder.getOrderPrice()) < 0) {
            return sellOrder.getOrderPrice();
        }
        if (price.compareTo(buyOrder.getOrderPrice()) > 0) {
            return buyOrder.getOrderPrice();
        }
        return price;
    }

}
