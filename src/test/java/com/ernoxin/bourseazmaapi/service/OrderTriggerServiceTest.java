package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderTriggerServiceTest {

    private TradingOrderRepository orderRepository;
    private MarketLiquidityService liquidityService;
    private OrderMatchingService matchingService;
    private UserRepository userRepository;
    private PortfolioHoldingRepository holdingRepository;
    private OrderTriggerService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(TradingOrderRepository.class);
        liquidityService = mock(MarketLiquidityService.class);
        matchingService = mock(OrderMatchingService.class);
        userRepository = mock(UserRepository.class);
        holdingRepository = mock(PortfolioHoldingRepository.class);
        service = new OrderTriggerService(
                orderRepository,
                liquidityService,
                matchingService,
                userRepository,
                holdingRepository
        );
    }

    @Test
    void equalTriggerUsesConfiguredHalfRialToleranceAtBoundary() {
        User user = user(new BigDecimal("10000"));
        TradingOrder order = pendingBuy(user, TriggerComparator.EQUAL, "100.00", "10.00", 10);
        arrange(order, user, "100.50");
        when(orderRepository.sumReservedBuyValueExcluding(1L, 10L)).thenReturn(BigDecimal.ZERO);

        int triggered = service.evaluatePendingTriggers();

        assertThat(triggered).isOne();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REQUESTED);
        assertThat(order.getLivePrice()).isEqualByComparingTo("100.50");
        verify(matchingService).matchOrder(order);
    }

    @Test
    void equalTriggerOutsideToleranceRemainsPendingWithoutMatching() {
        User user = user(new BigDecimal("10000"));
        TradingOrder order = pendingBuy(user, TriggerComparator.EQUAL, "100.00", "10.00", 10);
        arrange(order, user, "100.51");

        int triggered = service.evaluatePendingTriggers();

        assertThat(triggered).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.TRIGGER_PENDING);
        verify(matchingService, never()).matchOrder(any());
    }

    @Test
    void triggerFailsAtomicallyWhenOtherReservationsConsumedBuyingPower() {
        User user = user(new BigDecimal("100.00"));
        TradingOrder order = pendingBuy(user, TriggerComparator.GREATER_THAN, "50.00", "10.00", 10);
        arrange(order, user, "60.00");
        when(orderRepository.sumReservedBuyValueExcluding(1L, 10L)).thenReturn(new BigDecimal("1.00"));

        int triggered = service.evaluatePendingTriggers();

        assertThat(triggered).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getRemainingQuantity()).isZero();
        assertThat(order.getCancelledAt()).isNotNull();
        verify(matchingService, never()).matchOrder(any());
    }

    @Test
    void blockedAccountCannotBeReactivatedByConditionalOrderScheduler() {
        User user = user(new BigDecimal("10000"));
        user.setBlocked(true);
        TradingOrder order = pendingBuy(user, TriggerComparator.GREATER_THAN, "50.00", "10.00", 10);
        arrange(order, user, "60.00");
        when(orderRepository.sumReservedBuyValueExcluding(1L, 10L)).thenReturn(BigDecimal.ZERO);

        int triggered = service.evaluatePendingTriggers();

        assertThat(triggered).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getRemainingQuantity()).isZero();
        verify(matchingService, never()).matchOrder(any());
    }

    private void arrange(TradingOrder order, User user, String referencePrice) {
        when(orderRepository.findIdsByStatusOrderByOrderTimeAsc(OrderStatus.TRIGGER_PENDING))
                .thenReturn(List.of(order.getId()));
        when(orderRepository.findUserIdByOrderId(order.getId())).thenReturn(Optional.of(user.getId()));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(liquidityService.getReferencePrice(order.getInstrumentCode()))
                .thenReturn(Optional.of(new BigDecimal(referencePrice)));
    }

    private User user(BigDecimal balance) {
        User user = new User();
        user.setId(1L);
        user.setUsername("trigger-user");
        user.setRole(UserRole.USER);
        user.setBalance(balance);
        return user;
    }

    private TradingOrder pendingBuy(User user, TriggerComparator comparator, String triggerPrice,
                                    String orderPrice, long quantity) {
        TradingOrder order = new TradingOrder();
        order.setId(10L);
        order.setUser(user);
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.CONDITIONAL);
        order.setPriceType(PriceType.CUSTOM);
        order.setSymbol("TEST");
        order.setInstrumentCode("IRO1TEST0001");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(new BigDecimal(orderPrice));
        order.setLivePrice(new BigDecimal(orderPrice));
        order.setTriggerComparator(comparator);
        order.setTriggerPrice(new BigDecimal(triggerPrice));
        order.setStatus(OrderStatus.TRIGGER_PENDING);
        order.setOrderTime(Instant.now());
        return order;
    }
}
