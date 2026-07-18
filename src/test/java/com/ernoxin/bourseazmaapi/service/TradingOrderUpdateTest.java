package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.TradingRulesProperties;
import com.ernoxin.bourseazmaapi.dto.UpdateTradingOrderRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TradingOrderUpdateTest {

    private TradingOrderRepository orderRepository;
    private PortfolioHoldingRepository holdingRepository;
    private UserRepository userRepository;
    private OrderMatchingService matchingService;
    private MarketStateService marketStateService;
    private TradingAccountServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        orderRepository = mock(TradingOrderRepository.class);
        holdingRepository = mock(PortfolioHoldingRepository.class);
        userRepository = mock(UserRepository.class);
        matchingService = mock(OrderMatchingService.class);
        MarketLiquidityService liquidityService = mock(MarketLiquidityService.class);
        marketStateService = mock(MarketStateService.class);
        TradingAccountResponseMapper responseMapper = mock(TradingAccountResponseMapper.class);
        PrivateOrderBookService orderBookService = mock(PrivateOrderBookService.class);
        TradingRulesProperties rules = new TradingRulesProperties(
                new BigDecimal("5000000"),
                new BigDecimal("1000000000000")
        );

        service = new TradingAccountServiceImpl(
                orderRepository,
                holdingRepository,
                userRepository,
                matchingService,
                liquidityService,
                marketStateService,
                responseMapper,
                orderBookService,
                rules,
                mock(OrderUpdateNotifier.class)
        );

        user = new User();
        user.setId(1L);
        user.setBalance(new BigDecimal("100000000"));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(marketStateService.isMarketOpen()).thenReturn(true);
    }

    @Test
    void updatesOnlyTheUnfilledPartAndExcludesCurrentSellReservation() {
        TradingOrder order = activeSellOrder();
        PortfolioHolding holding = new PortfolioHolding();
        holding.setQuantity(100L);

        when(orderRepository.findByIdAndUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(order));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(1L, "IRO1TEST0001"))
                .thenReturn(List.of(holding));
        when(orderRepository.sumReservedSellQuantityExcluding(1L, "IRO1TEST0001", 10L))
                .thenReturn(30L);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(matchingService.matchOrder(order)).thenReturn(List.of());

        UpdateTradingOrderRequest request = new UpdateTradingOrderRequest();
        request.setQuantity(70L);
        request.setPrice(new BigDecimal("120000"));

        service.updateOrder(1L, 10L, request);

        assertThat(order.getQuantity()).isEqualTo(70L);
        assertThat(order.getExecutedQuantity()).isEqualTo(20L);
        assertThat(order.getRemainingQuantity()).isEqualTo(50L);
        assertThat(order.getOrderPrice()).isEqualByComparingTo("120000.00");
        assertThat(order.getOrderTime()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        verify(orderRepository).sumReservedSellQuantityExcluding(1L, "IRO1TEST0001", 10L);
        verify(matchingService).matchOrder(order);
        var lockOrder = inOrder(userRepository, orderRepository);
        lockOrder.verify(userRepository).findByIdForUpdate(1L);
        lockOrder.verify(orderRepository).findByIdAndUserIdForUpdate(10L, 1L);
    }

    @Test
    void rejectsTotalQuantityThatWouldEraseAlreadyExecutedShares() {
        TradingOrder order = activeSellOrder();
        when(orderRepository.findByIdAndUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(order));

        UpdateTradingOrderRequest request = new UpdateTradingOrderRequest();
        request.setQuantity(20L);
        request.setPrice(new BigDecimal("120000"));

        assertThatThrownBy(() -> service.updateOrder(1L, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSellIncreaseBeyondHoldingsAfterOtherReservations() {
        TradingOrder order = activeSellOrder();
        PortfolioHolding holding = new PortfolioHolding();
        holding.setQuantity(100L);

        when(orderRepository.findByIdAndUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(order));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(1L, "IRO1TEST0001"))
                .thenReturn(List.of(holding));
        when(orderRepository.sumReservedSellQuantityExcluding(1L, "IRO1TEST0001", 10L))
                .thenReturn(40L);

        UpdateTradingOrderRequest request = new UpdateTradingOrderRequest();
        request.setQuantity(90L); // 20 executed + 70 remaining; only 60 are available.
        request.setPrice(new BigDecimal("120000"));

        assertThatThrownBy(() -> service.updateOrder(1L, 10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60");
    }

    private TradingOrder activeSellOrder() {
        TradingOrder order = new TradingOrder();
        order.setId(10L);
        order.setUser(user);
        order.setSide(OrderSide.SELL);
        order.setSymbol("تست");
        order.setInstrumentCode("IRO1TEST0001");
        order.setQuantity(100L);
        order.setExecutedQuantity(20L);
        order.setRemainingQuantity(80L);
        order.setOrderPrice(new BigDecimal("100000"));
        order.setLivePrice(new BigDecimal("100000"));
        order.setAverageExecutedPrice(new BigDecimal("100000"));
        order.setOrderTime(Instant.parse("2026-01-01T00:00:00Z"));
        order.setStatus(OrderStatus.PARTIALLY_FILLED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        return order;
    }
}
