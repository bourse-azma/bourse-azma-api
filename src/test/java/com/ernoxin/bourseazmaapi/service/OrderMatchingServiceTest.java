package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.MarketOrderMatcher;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderMatchingServiceTest {

    @Mock
    private TradingOrderRepository orderRepository;
    @Mock
    private MarketOrderMatcher marketOrderMatcher;

    private OrderMatchingService service;

    @BeforeEach
    void setUp() {
        service = new OrderMatchingService(orderRepository, marketOrderMatcher);
    }

    @Test
    void unfilledMarketOrderIsClosedAndCannotRestOnBook() {
        TradingOrder order = activeOrder(7L, OrderSide.BUY, PriceType.MARKET, 10);
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(marketOrderMatcher.matchBuyAgainstMarket(order)).thenReturn(List.of());

        service.matchOrder(order);

        assertThat(order.getRemainingQuantity()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.isCancellable()).isFalse();
        verify(orderRepository).save(order);
    }

    @Test
    void limitOrderWithoutCrossingLiquidityKeepsItsQueuePosition() {
        TradingOrder order = activeOrder(8L, OrderSide.SELL, PriceType.CUSTOM, 6);
        when(orderRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(order));
        when(marketOrderMatcher.matchSellAgainstMarket(order)).thenReturn(List.of());

        service.matchOrder(order);

        assertThat(order.getRemainingQuantity()).isEqualTo(6);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REQUESTED);
        assertThat(order.getCancelledAt()).isNull();
        verify(orderRepository, never()).save(order);
    }

    @Test
    void partiallyExecutedMarketOrderKeepsFillStatusButClosesUnfilledRemainder() {
        TradingOrder order = activeOrder(9L, OrderSide.BUY, PriceType.MARKET, 10);
        Trade trade = new Trade();
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(marketOrderMatcher.matchBuyAgainstMarket(order)).thenAnswer(invocation -> {
            order.setExecutedQuantity(4L);
            order.setRemainingQuantity(6L);
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
            return List.of(trade);
        });

        List<Trade> result = service.matchOrder(order);

        assertThat(result).containsExactly(trade);
        assertThat(order.getExecutedQuantity()).isEqualTo(4);
        assertThat(order.getRemainingQuantity()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.isCancellable()).isFalse();
        verify(orderRepository).save(order);
    }

    @Test
    void schedulerScopesEachMatchingPassToOneUsersPrivateBook() {
        when(orderRepository.findActiveOrderIdsForPrivateBook(
                22L, "INS", List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED)))
                .thenReturn(List.of(11L));
        TradingOrder order = activeOrder(11L, OrderSide.BUY, PriceType.CUSTOM, 5);
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));
        when(marketOrderMatcher.matchBuyAgainstMarket(order)).thenReturn(List.of());

        service.runMatchingForUserInstrument(22L, "INS");

        verify(orderRepository).findActiveOrderIdsForPrivateBook(
                22L, "INS", List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED));
        verify(marketOrderMatcher).matchBuyAgainstMarket(order);
        verifyNoMoreInteractions(marketOrderMatcher);
    }

    private TradingOrder activeOrder(Long id, OrderSide side, PriceType priceType, long remaining) {
        TradingOrder order = new TradingOrder();
        order.setId(id);
        order.setSide(side);
        order.setPriceType(priceType);
        order.setStatus(OrderStatus.REQUESTED);
        order.setQuantity(remaining);
        order.setRemainingQuantity(remaining);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(new BigDecimal("100"));
        order.setOrderTime(Instant.now());
        return order;
    }
}
