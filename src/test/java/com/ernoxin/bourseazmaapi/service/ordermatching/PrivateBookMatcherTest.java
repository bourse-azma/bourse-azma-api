package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.PrivateBookStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivateBookMatcherTest {

    private TradingOrderRepository orderRepository;
    private PrivateBookStateService bookStateService;
    private MarketMakerService marketMakerService;
    private TradeExecutor tradeExecutor;
    private PrivateBookMatcher matcher;

    @BeforeEach
    void setUp() {
        orderRepository = mock(TradingOrderRepository.class);
        bookStateService = mock(PrivateBookStateService.class);
        marketMakerService = mock(MarketMakerService.class);
        tradeExecutor = mock(TradeExecutor.class);
        matcher = new PrivateBookMatcher(orderRepository, bookStateService, marketMakerService, tradeExecutor);
    }

    @Test
    void incomingBuyConsumesOwnSellsInPriceTimeOrderAtEachMakerPrice() {
        User user = user(1L, "user");
        TradingOrder incoming = order(10L, user, OrderSide.BUY, 10, "100", Instant.now());
        TradingOrder cheap = order(11L, user, OrderSide.SELL, 4, "90", Instant.now().minusSeconds(2));
        TradingOrder next = order(12L, user, OrderSide.SELL, 10, "95", Instant.now().minusSeconds(1));
        when(orderRepository.findOwnRestingSellsForMatch(anyLong(), anyString(), any(), anyList(), anyLong()))
                .thenReturn(List.of(cheap, next));
        when(orderRepository.findByIdForUpdate(cheap.getId())).thenReturn(Optional.of(cheap));
        when(orderRepository.findByIdForUpdate(next.getId())).thenReturn(Optional.of(next));
        stubSuccessfulExecution();

        when(bookStateService.loadResidualAskLevels(1L, "CODE")).thenReturn(List.of());

        List<Trade> trades = matcher.matchFully(incoming);

        assertThat(trades).hasSize(2);
        assertThat(incoming.getRemainingQuantity()).isZero();
        assertThat(cheap.getRemainingQuantity()).isZero();
        assertThat(next.getRemainingQuantity()).isEqualTo(4L);
        verify(tradeExecutor).executeTrade(incoming, cheap, 4L, new BigDecimal("90"));
        verify(tradeExecutor).executeTrade(incoming, next, 6L, new BigDecimal("95"));
    }

    @Test
    void incomingBuyUsesBetterPublicPriceBeforeAUsersMoreExpensiveRestingSell() {
        User user = user(1L, "user");
        User maker = user(99L, MarketMakerService.MARKET_MAKER_USERNAME);
        TradingOrder incoming = order(10L, user, OrderSide.BUY, 7, "105", Instant.now());
        TradingOrder ownSell = order(11L, user, OrderSide.SELL, 3, "101", Instant.now().minusSeconds(1));
        TradingOrder marketSell = order(20L, maker, OrderSide.SELL, 4, "100", Instant.now());
        when(orderRepository.findOwnRestingSellsForMatch(
                eq(1L), eq("CODE"), eq(new BigDecimal("105")), anyList(), eq(10L)))
                .thenReturn(List.of(ownSell));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ownSell));
        when(bookStateService.loadResidualAskLevels(1L, "CODE")).thenReturn(List.of(
                new ResidualBookLevel(new BigDecimal("100"), 4, 1, 4)
        ));
        when(marketMakerService.createCompletedCounterOrder(
                incoming, OrderSide.SELL, 4L, new BigDecimal("100"))).thenReturn(marketSell);
        stubSuccessfulExecution();

        assertThat(matcher.matchFully(incoming)).hasSize(2);

        var executionOrder = inOrder(tradeExecutor);
        executionOrder.verify(tradeExecutor)
                .executeTrade(incoming, marketSell, 4L, new BigDecimal("100"));
        executionOrder.verify(tradeExecutor)
                .executeTrade(incoming, ownSell, 3L, new BigDecimal("101"));
        verify(bookStateService).consume(1L, "CODE", BookSide.ASK, new BigDecimal("100"), 4L);
        assertThat(incoming.getRemainingQuantity()).isZero();
        assertThat(ownSell.getRemainingQuantity()).isZero();
    }

    @Test
    void incomingSellUsesBetterPublicBidBeforeAUsersLowerRestingBuy() {
        User user = user(1L, "user");
        User maker = user(99L, MarketMakerService.MARKET_MAKER_USERNAME);
        TradingOrder incoming = order(10L, user, OrderSide.SELL, 7, "95", Instant.now());
        TradingOrder ownBuy = order(11L, user, OrderSide.BUY, 3, "99", Instant.now().minusSeconds(1));
        TradingOrder marketBuy = order(20L, maker, OrderSide.BUY, 4, "100", Instant.now());
        when(orderRepository.findOwnRestingBuysForMatch(
                eq(1L), eq("CODE"), eq(new BigDecimal("95")), anyList(), eq(10L)))
                .thenReturn(List.of(ownBuy));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ownBuy));
        when(bookStateService.loadResidualBidLevels(1L, "CODE")).thenReturn(List.of(
                new ResidualBookLevel(new BigDecimal("100"), 4, 1, 4)
        ));
        when(marketMakerService.createCompletedCounterOrder(
                incoming, OrderSide.BUY, 4L, new BigDecimal("100"))).thenReturn(marketBuy);
        stubSuccessfulExecution();

        assertThat(matcher.matchFully(incoming)).hasSize(2);

        var executionOrder = inOrder(tradeExecutor);
        executionOrder.verify(tradeExecutor)
                .executeTrade(marketBuy, incoming, 4L, new BigDecimal("100"));
        executionOrder.verify(tradeExecutor)
                .executeTrade(ownBuy, incoming, 3L, new BigDecimal("99"));
        verify(bookStateService).consume(1L, "CODE", BookSide.BID, new BigDecimal("100"), 4L);
        assertThat(incoming.getRemainingQuantity()).isZero();
        assertThat(ownBuy.getRemainingQuantity()).isZero();
    }

    @Test
    void publicLiquidityWinsAtTheSamePriceBecauseItWasAlreadyResting() {
        User user = user(1L, "user");
        User maker = user(99L, MarketMakerService.MARKET_MAKER_USERNAME);
        TradingOrder incoming = order(10L, user, OrderSide.BUY, 2, "100", Instant.now());
        TradingOrder ownSell = order(11L, user, OrderSide.SELL, 2, "100", Instant.now().minusSeconds(1));
        TradingOrder marketSell = order(20L, maker, OrderSide.SELL, 2, "100", Instant.now());
        when(orderRepository.findOwnRestingSellsForMatch(anyLong(), anyString(), any(), anyList(), anyLong()))
                .thenReturn(List.of(ownSell));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ownSell));
        when(bookStateService.loadResidualAskLevels(1L, "CODE")).thenReturn(List.of(
                new ResidualBookLevel(new BigDecimal("100"), 2, 1, 2)
        ));
        when(marketMakerService.createCompletedCounterOrder(
                incoming, OrderSide.SELL, 2L, new BigDecimal("100"))).thenReturn(marketSell);
        stubSuccessfulExecution();

        assertThat(matcher.matchFully(incoming)).hasSize(1);

        verify(tradeExecutor).executeTrade(incoming, marketSell, 2L, new BigDecimal("100"));
        verify(tradeExecutor, never()).executeTrade(incoming, ownSell, 2L, new BigDecimal("100"));
        assertThat(ownSell.getRemainingQuantity()).isEqualTo(2L);
    }

    @Test
    void schedulerRerunUsesTheOlderRestingOrdersPriceForAnOwnCross() {
        User user = user(1L, "user");
        Instant now = Instant.now();
        TradingOrder olderBuy = order(10L, user, OrderSide.BUY, 2, "105", now.minusSeconds(5));
        TradingOrder newerSell = order(11L, user, OrderSide.SELL, 2, "100", now);
        when(orderRepository.findOwnRestingSellsForMatch(anyLong(), anyString(), any(), anyList(), anyLong()))
                .thenReturn(List.of(newerSell));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(newerSell));
        when(bookStateService.loadResidualAskLevels(1L, "CODE")).thenReturn(List.of());
        stubSuccessfulExecution();

        assertThat(matcher.matchFully(olderBuy)).hasSize(1);

        verify(tradeExecutor).executeTrade(olderBuy, newerSell, 2L, new BigDecimal("105"));
    }

    @Test
    void residualLiquidityIsConsumedOnlyAfterSuccessfulFinancialExecution() {
        User user = user(1L, "user");
        User maker = user(99L, MarketMakerService.MARKET_MAKER_USERNAME);
        TradingOrder incoming = order(10L, user, OrderSide.BUY, 7, "101", Instant.now());
        when(bookStateService.loadResidualAskLevels(1L, "CODE")).thenReturn(List.of(
                new ResidualBookLevel(new BigDecimal("100"), 5, 1, 5),
                new ResidualBookLevel(new BigDecimal("101"), 5, 1, 5)
        ));
        when(marketMakerService.createCompletedCounterOrder(any(), eq(OrderSide.SELL), anyLong(), any()))
                .thenAnswer(invocation -> order(
                        100L + invocation.getArgument(2, Long.class), maker, OrderSide.SELL,
                        invocation.getArgument(2, Long.class),
                        invocation.getArgument(3, BigDecimal.class).toPlainString(), Instant.now()));
        stubSuccessfulExecution();

        List<Trade> trades = matcher.matchFully(incoming);

        assertThat(trades).hasSize(2);
        assertThat(incoming.getRemainingQuantity()).isZero();
        verify(bookStateService).consume(1L, "CODE", BookSide.ASK, new BigDecimal("100"), 5L);
        verify(bookStateService).consume(1L, "CODE", BookSide.ASK, new BigDecimal("101"), 2L);
        verify(marketMakerService, never()).cancelUnmatchedCounterOrder(any());
    }

    @Test
    void failedResidualExecutionCancelsSyntheticCounterOrderAndDoesNotConsumeDepth() {
        User user = user(1L, "user");
        TradingOrder incoming = order(10L, user, OrderSide.SELL, 5, "90", Instant.now());
        TradingOrder counter = order(20L, user(99L, MarketMakerService.MARKET_MAKER_USERNAME),
                OrderSide.BUY, 5, "95", Instant.now());
        when(bookStateService.loadResidualBidLevels(1L, "CODE"))
                .thenReturn(List.of(new ResidualBookLevel(new BigDecimal("95"), 5, 1, 5)));
        when(marketMakerService.createCompletedCounterOrder(incoming, OrderSide.BUY, 5L, new BigDecimal("95")))
                .thenReturn(counter);
        when(tradeExecutor.executeTrade(counter, incoming, 5L, new BigDecimal("95"))).thenReturn(null);

        assertThat(matcher.matchFully(incoming)).isEmpty();

        verify(marketMakerService).cancelUnmatchedCounterOrder(counter);
        verify(bookStateService).loadResidualBidLevels(1L, "CODE");
        verify(bookStateService, never()).consume(anyLong(), anyString(), any(), any(), anyLong());
        assertThat(incoming.getRemainingQuantity()).isEqualTo(5L);
    }

    private void stubSuccessfulExecution() {
        when(tradeExecutor.executeTrade(any(), any(), anyLong(), any())).thenAnswer(invocation -> {
            TradingOrder buy = invocation.getArgument(0);
            TradingOrder sell = invocation.getArgument(1);
            long quantity = invocation.getArgument(2);
            applyFill(buy, quantity);
            applyFill(sell, quantity);
            return new Trade();
        });
    }

    private void applyFill(TradingOrder order, long quantity) {
        order.setExecutedQuantity(order.getExecutedQuantity() + quantity);
        order.setRemainingQuantity(order.getRemainingQuantity() - quantity);
        order.setStatus(order.getRemainingQuantity() == 0 ? OrderStatus.COMPLETED : OrderStatus.PARTIALLY_FILLED);
    }

    private TradingOrder order(Long id, User user, OrderSide side, long quantity, String price, Instant time) {
        TradingOrder order = new TradingOrder();
        order.setId(id);
        order.setUser(user);
        order.setSide(side);
        order.setSymbol("TEST");
        order.setInstrumentCode("CODE");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(new BigDecimal(price));
        order.setLivePrice(new BigDecimal(price));
        order.setOrderTime(time);
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        return order;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(UserRole.USER);
        return user;
    }
}
