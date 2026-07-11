package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.PrivateBookStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivateBookMatcherTest {

    @Mock
    private TradingOrderRepository orderRepository;
    @Mock
    private PrivateBookStateService privateBookStateService;
    @Mock
    private MarketMakerService marketMakerService;
    @Mock
    private TradeExecutor tradeExecutor;

    private PrivateBookMatcher matcher;
    private User user;

    @BeforeEach
    void setUp() {
        matcher = new PrivateBookMatcher(orderRepository, privateBookStateService,
                marketMakerService, tradeExecutor);
        user = new User();
        user.setId(5L);
        user.setUsername("trader");
    }

    @Test
    void buyMatchesOwnRestingSellBeforeMarketAtMakerPrice() {
        TradingOrder buy = order(10L, OrderSide.BUY, "100", 8);
        TradingOrder sell = order(20L, OrderSide.SELL, "99", 5);
        when(orderRepository.findOwnRestingSellsForMatch(
                eq(5L), eq("INS"), eq(bd("100")), anyList(), eq(10L)))
                .thenReturn(List.of(sell));
        when(orderRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(sell));
        Trade trade = new Trade();
        when(tradeExecutor.executeTrade(buy, sell, 5, bd("99"))).thenAnswer(invocation -> {
            buy.setRemainingQuantity(3L);
            buy.setExecutedQuantity(5L);
            sell.setRemainingQuantity(0L);
            sell.setExecutedQuantity(5L);
            sell.setStatus(OrderStatus.COMPLETED);
            return trade;
        });
        when(privateBookStateService.loadResidualAskLevels(5L, "INS")).thenReturn(List.of(
                new ResidualBookLevel(bd("100"), 1000, 2, 1000)
        ));
        TradingOrder counter = order(99L, OrderSide.SELL, "100", 3);
        when(marketMakerService.createCompletedCounterOrder(buy, OrderSide.SELL, 3, bd("100")))
                .thenReturn(counter);
        Trade marketTrade = new Trade();
        when(tradeExecutor.executeTrade(buy, counter, 3, bd("100"))).thenAnswer(invocation -> {
            buy.setRemainingQuantity(0L);
            buy.setExecutedQuantity(8L);
            buy.setStatus(OrderStatus.COMPLETED);
            return marketTrade;
        });

        List<Trade> trades = matcher.matchFully(buy);

        assertThat(trades).containsExactly(trade, marketTrade);
        verify(privateBookStateService).consume(5L, "INS", BookSide.ASK, bd("100"), 3L);
        verify(tradeExecutor).executeTrade(buy, sell, 5, bd("99"));
    }

    @Test
    void residualMarketCannotBeTakenBeyondRemainingDepth() {
        TradingOrder buy = order(11L, OrderSide.BUY, "100", 50);
        when(orderRepository.findOwnRestingSellsForMatch(any(), any(), any(), anyList(), any()))
                .thenReturn(List.of());
        when(privateBookStateService.loadResidualAskLevels(5L, "INS")).thenReturn(List.of(
                new ResidualBookLevel(bd("100"), 100, 1, 20)
        ));
        TradingOrder counter = order(98L, OrderSide.SELL, "100", 20);
        when(marketMakerService.createCompletedCounterOrder(buy, OrderSide.SELL, 20, bd("100")))
                .thenReturn(counter);
        when(tradeExecutor.executeTrade(buy, counter, 20, bd("100"))).thenAnswer(invocation -> {
            buy.setRemainingQuantity(30L);
            buy.setExecutedQuantity(20L);
            buy.setStatus(OrderStatus.PARTIALLY_FILLED);
            return new Trade();
        });

        List<Trade> trades = matcher.matchFully(buy);

        assertThat(trades).hasSize(1);
        assertThat(buy.getRemainingQuantity()).isEqualTo(30);
        verify(privateBookStateService).consume(5L, "INS", BookSide.ASK, bd("100"), 20L);
        verify(marketMakerService, times(1)).createCompletedCounterOrder(any(), any(), anyLong(), any());
    }

    @Test
    void collapseSelfCrossesMatchesBestBidAndAsk() {
        TradingOrder buy = order(1L, OrderSide.BUY, "105", 10);
        TradingOrder sell = order(2L, OrderSide.SELL, "100", 10);
        buy.setOrderTime(Instant.parse("2026-01-01T10:00:00Z"));
        sell.setOrderTime(Instant.parse("2026-01-01T11:00:00Z"));
        when(orderRepository.findActiveBuysPriceTime(eq(5L), eq("INS"), anyList()))
                .thenReturn(List.of(buy), List.of());
        when(orderRepository.findActiveSellsPriceTime(eq(5L), eq("INS"), anyList()))
                .thenReturn(List.of(sell), List.of());
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(buy));
        when(orderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(sell));
        when(tradeExecutor.executeTrade(eq(buy), eq(sell), eq(10L), eq(bd("105"))))
                .thenReturn(new Trade());

        List<Trade> trades = matcher.collapseSelfCrosses(5L, "INS");

        assertThat(trades).hasSize(1);
        // Earlier resting buy is maker → price 105
        verify(tradeExecutor).executeTrade(buy, sell, 10L, bd("105"));
    }

    private TradingOrder order(Long id, OrderSide side, String price, long qty) {
        TradingOrder order = new TradingOrder();
        order.setId(id);
        order.setUser(user);
        order.setSide(side);
        order.setSymbol("SYM");
        order.setInstrumentCode("INS");
        order.setQuantity(qty);
        order.setRemainingQuantity(qty);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(bd(price));
        order.setLivePrice(bd(price));
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        return order;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
