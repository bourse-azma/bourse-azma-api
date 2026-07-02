package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.Trade;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PeerOrderMatcher {

    private final TradingOrderRepository tradingOrderRepository;
    private final MarketMakerService marketMakerService;
    private final TradeExecutor tradeExecutor;

    public List<Trade> matchBuyOrder(TradingOrder buyOrder, List<OrderStatus> activeStatuses) {
        List<Trade> trades = new ArrayList<>();

        List<TradingOrder> sellOrders = tradingOrderRepository.findActiveSellOrders(
                buyOrder.getInstrumentCode(), OrderSide.SELL, activeStatuses);

        for (TradingOrder sellOrder : sellOrders) {
            if (buyOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (sellOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            if (marketMakerService.isMarketMaker(sellOrder.getUser())) {
                continue;
            }

            if (buyOrder.getOrderPrice().compareTo(sellOrder.getOrderPrice()) < 0) {
                break;
            }

            BigDecimal executionPrice = sellOrder.getOrderPrice();
            long matchQuantity = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());

            Trade trade = tradeExecutor.executeTrade(buyOrder, sellOrder, matchQuantity, executionPrice);
            if (trade != null) {
                trades.add(trade);
            }
        }

        return trades;
    }

    public List<Trade> matchSellOrder(TradingOrder sellOrder, List<OrderStatus> activeStatuses) {
        List<Trade> trades = new ArrayList<>();

        List<TradingOrder> buyOrders = tradingOrderRepository.findActiveBuyOrders(
                sellOrder.getInstrumentCode(), OrderSide.BUY, activeStatuses);

        for (TradingOrder buyOrder : buyOrders) {
            if (sellOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (buyOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            if (marketMakerService.isMarketMaker(buyOrder.getUser())) {
                continue;
            }

            if (sellOrder.getOrderPrice().compareTo(buyOrder.getOrderPrice()) > 0) {
                break;
            }

            BigDecimal executionPrice = buyOrder.getOrderPrice();
            long matchQuantity = Math.min(sellOrder.getRemainingQuantity(), buyOrder.getRemainingQuantity());

            Trade trade = tradeExecutor.executeTrade(buyOrder, sellOrder, matchQuantity, executionPrice);
            if (trade != null) {
                trades.add(trade);
            }
        }

        return trades;
    }
}
