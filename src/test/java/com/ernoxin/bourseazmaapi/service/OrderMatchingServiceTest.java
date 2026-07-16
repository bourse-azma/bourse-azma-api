package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.PrivateBookMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class OrderMatchingServiceTest {

    private TradingOrderRepository orderRepository;
    private UserRepository userRepository;
    private PrivateBookMatcher matcher;
    private OrderMatchingService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(TradingOrderRepository.class);
        userRepository = mock(UserRepository.class);
        matcher = mock(PrivateBookMatcher.class);
        service = new OrderMatchingService(orderRepository, userRepository, matcher);
    }

    @Test
    void rejectsDetachedNullOrForeignOrderBeforeCallingMatcher() {
        assertThat(service.matchOrder(null)).isEmpty();
        TradingOrder detached = activeOrder(1L, 10L, PriceType.CUSTOM, 5L);
        detached.setId(null);
        assertThat(service.matchOrder(detached)).isEmpty();

        verifyNoInteractions(userRepository, matcher);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void unfilledMarketOrderIsFailedAndReleasesItsEntireReservation() {
        TradingOrder order = activeOrder(1L, 10L, PriceType.MARKET, 5L);
        arrangeLocked(order);
        when(matcher.matchFully(order)).thenReturn(List.of());

        assertThat(service.matchOrder(order)).isEmpty();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getRemainingQuantity()).isZero();
        assertThat(order.getCancelledAt()).isNotNull();
        verify(orderRepository).save(order);
    }

    @Test
    void partiallyFilledMarketOrderIsClosedWithoutMislabelingExecutedPartAsFailure() {
        TradingOrder order = activeOrder(1L, 10L, PriceType.MARKET, 5L);
        arrangeLocked(order);
        Trade trade = new Trade();
        when(matcher.matchFully(order)).thenAnswer(invocation -> {
            order.setExecutedQuantity(2L);
            order.setRemainingQuantity(3L);
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
            return List.of(trade);
        });

        assertThat(service.matchOrder(order)).containsExactly(trade);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getExecutedQuantity()).isEqualTo(2L);
        assertThat(order.getRemainingQuantity()).isZero();
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void rerunUsesCombinedBookMatchingAndSkipsStaleIds() {
        User user = user(1L);
        TradingOrder activeBuy = activeOrder(10L, 1L, PriceType.CUSTOM, 5L);
        TradingOrder staleSell = activeOrder(11L, 1L, PriceType.CUSTOM, 5L);
        staleSell.setSide(OrderSide.SELL);
        staleSell.setStatus(OrderStatus.CANCELLED);
        staleSell.setRemainingQuantity(0L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        Trade marketTrade = new Trade();
        when(orderRepository.findActiveBuyOrderIdsForMatching(eq(1L), eq("CODE"), anyList()))
                .thenReturn(List.of(10L));
        when(orderRepository.findActiveSellOrderIdsForMatching(eq(1L), eq("CODE"), anyList()))
                .thenReturn(List.of(11L));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeBuy));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(staleSell));
        when(matcher.matchFully(activeBuy)).thenReturn(List.of(marketTrade));

        assertThat(service.runMatchingForUserInstrument(1L, "CODE"))
                .containsExactly(marketTrade);

        verify(matcher).matchFully(activeBuy);
        verify(matcher, never()).matchFully(staleSell);
    }

    private void arrangeLocked(TradingOrder order) {
        when(userRepository.findByIdForUpdate(order.getUser().getId()))
                .thenReturn(Optional.of(order.getUser()));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    private TradingOrder activeOrder(Long orderId, Long userId, PriceType priceType, long quantity) {
        TradingOrder order = new TradingOrder();
        order.setId(orderId);
        order.setUser(user(userId));
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(priceType);
        order.setSymbol("TEST");
        order.setInstrumentCode("CODE");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(BigDecimal.TEN);
        order.setLivePrice(BigDecimal.TEN);
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        return order;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRole(UserRole.USER);
        return user;
    }
}
