package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.model.OrderSide;
import com.ernoxin.boorsazmaapi.model.OrderStatus;
import com.ernoxin.boorsazmaapi.model.TradingOrder;
import com.ernoxin.boorsazmaapi.model.User;
import com.ernoxin.boorsazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.boorsazmaapi.repository.TradeRepository;
import com.ernoxin.boorsazmaapi.repository.TradingOrderRepository;
import com.ernoxin.boorsazmaapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMatchingServiceTest {

    @Mock
    private TradingOrderRepository tradingOrderRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MarketLiquidityService marketLiquidityService;
    @Mock
    private MarketMakerService marketMakerService;

    @InjectMocks
    private OrderMatchingService orderMatchingService;

    private User buyer;
    private User marketMaker;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(10L);
        buyer.setUsername("erfan");
        buyer.setBalance(new BigDecimal("100000000"));

        marketMaker = new User();
        marketMaker.setId(99L);
        marketMaker.setUsername(MarketMakerService.MARKET_MAKER_USERNAME);
    }

    @Test
    void matchBuyOrder_executesAgainstTsetmcAskWhenPriceMatches() {
        TradingOrder buyOrder = buildBuyOrder(new BigDecimal("575"), 10_000L);
        TradingOrder marketSellOrder = buildMarketSellOrder(10_000L, new BigDecimal("575"));

        when(tradingOrderRepository.findActiveSellOrders(
                eq("44891482026867833"), eq(OrderSide.SELL), any())).thenReturn(List.of());
        when(marketLiquidityService.getAskLevels("44891482026867833")).thenReturn(List.of(
                new MarketLiquidityLevel(1, new BigDecimal("575"), 6_160_001L)
        ));
        when(marketMakerService.createCompletedCounterOrder(buyOrder, OrderSide.SELL, 10_000L, new BigDecimal("575")))
                .thenReturn(marketSellOrder);
        when(marketMakerService.isMarketMaker(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user != null && MarketMakerService.MARKET_MAKER_USERNAME.equals(user.getUsername());
        });
        when(portfolioHoldingRepository.findAllByUserIdAndInstrumentCode(10L, "44891482026867833"))
                .thenReturn(List.of());
        when(userRepository.getReferenceById(10L)).thenReturn(buyer);
        when(tradingOrderRepository.save(any(TradingOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.findAllByBuyOrderIdOrSellOrderIdOrderByExecutedAtDesc(1L, -1L)).thenReturn(List.of());

        List<?> trades = orderMatchingService.matchOrder(buyOrder);

        assertThat(trades).hasSize(1);
        assertThat(buyOrder.getExecutedQuantity()).isEqualTo(10_000L);
        assertThat(buyOrder.getRemainingQuantity()).isZero();
        assertThat(buyOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo("94250000");
    }

    private TradingOrder buildBuyOrder(BigDecimal price, long quantity) {
        TradingOrder order = new TradingOrder();
        order.setId(1L);
        order.setUser(buyer);
        order.setSide(OrderSide.BUY);
        order.setSymbol("خساپا");
        order.setInstrumentCode("44891482026867833");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(price);
        order.setLivePrice(new BigDecimal("574"));
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        return order;
    }

    private TradingOrder buildMarketSellOrder(long quantity, BigDecimal price) {
        TradingOrder order = new TradingOrder();
        order.setId(2L);
        order.setUser(marketMaker);
        order.setSide(OrderSide.SELL);
        order.setSymbol("خساپا");
        order.setInstrumentCode("44891482026867833");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(price);
        order.setLivePrice(new BigDecimal("574"));
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        return order;
    }
}
