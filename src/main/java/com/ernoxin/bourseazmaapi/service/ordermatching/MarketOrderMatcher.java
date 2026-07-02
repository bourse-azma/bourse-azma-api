package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.Trade;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.service.MarketLiquidityLevel;
import com.ernoxin.bourseazmaapi.service.MarketLiquidityService;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MarketOrderMatcher {

    private final MarketLiquidityService marketLiquidityService;
    private final MarketMakerService marketMakerService;
    private final TradeExecutor tradeExecutor;

    public List<Trade> matchBuyAgainstMarket(TradingOrder buyOrder) {
        if (!marketLiquidityService.isOrderBookReady(buyOrder.getInstrumentCode())) {
            return List.of();
        }

        List<Trade> trades = new ArrayList<>();
        List<MarketLiquidityLevel> askLevels = marketLiquidityService.getAskLevels(buyOrder.getInstrumentCode());

        for (MarketLiquidityLevel level : askLevels) {
            if (buyOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (buyOrder.getOrderPrice().compareTo(level.price()) < 0) {
                break;
            }

            long matchQuantity = Math.min(buyOrder.getRemainingQuantity(), level.volume());
            if (matchQuantity <= 0) {
                continue;
            }

            TradingOrder marketSellOrder = marketMakerService.createCompletedCounterOrder(
                    buyOrder, OrderSide.SELL, matchQuantity, level.price());
            Trade trade = tradeExecutor.executeTrade(buyOrder, marketSellOrder, matchQuantity, level.price());
            if (trade != null) {
                trades.add(trade);
            } else {
                marketMakerService.cancelUnmatchedCounterOrder(marketSellOrder);
            }
        }

        return trades;
    }

    public List<Trade> matchSellAgainstMarket(TradingOrder sellOrder) {
        if (!marketLiquidityService.isOrderBookReady(sellOrder.getInstrumentCode())) {
            return List.of();
        }

        List<Trade> trades = new ArrayList<>();
        List<MarketLiquidityLevel> bidLevels = marketLiquidityService.getBidLevels(sellOrder.getInstrumentCode());

        for (MarketLiquidityLevel level : bidLevels) {
            if (sellOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (sellOrder.getOrderPrice().compareTo(level.price()) > 0) {
                break;
            }

            long matchQuantity = Math.min(sellOrder.getRemainingQuantity(), level.volume());
            if (matchQuantity <= 0) {
                continue;
            }

            TradingOrder marketBuyOrder = marketMakerService.createCompletedCounterOrder(
                    sellOrder, OrderSide.BUY, matchQuantity, level.price());
            Trade trade = tradeExecutor.executeTrade(marketBuyOrder, sellOrder, matchQuantity, level.price());
            if (trade != null) {
                trades.add(trade);
            } else {
                marketMakerService.cancelUnmatchedCounterOrder(marketBuyOrder);
            }
        }

        return trades;
    }
}
