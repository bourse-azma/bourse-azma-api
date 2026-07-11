package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradeRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.WalletLedgerService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeExecutorTest {

    @Mock
    private TradingOrderRepository orderRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private PortfolioHoldingRepository holdingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MarketMakerService marketMakerService;
    @Mock
    private WalletLedgerService walletLedgerService;

    private TradeExecutor executor;
    private User buyer;
    private User marketMaker;

    @BeforeEach
    void setUp() {
        executor = new TradeExecutor(orderRepository, tradeRepository, holdingRepository,
                userRepository, marketMakerService, walletLedgerService);
        buyer = user(1L, "buyer", "10000");
        marketMaker = user(2L, MarketMakerService.MARKET_MAKER_USERNAME, "0");
        lenient().when(marketMakerService.isMarketMaker(buyer)).thenReturn(false);
        lenient().when(marketMakerService.isMarketMaker(marketMaker)).thenReturn(true);
        lenient().when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        lenient().when(holdingRepository.findAllByUserIdAndInstrumentCode(1L, "INS")).thenReturn(List.of());
        lenient().when(userRepository.getReferenceById(1L)).thenReturn(buyer);
        lenient().when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void averageExecutionPriceIncludesEveryPartialFillWithQuantityWeighting() {
        TradingOrder buy = order(10L, buyer, OrderSide.BUY, 10);

        executor.executeTrade(buy, order(20L, marketMaker, OrderSide.SELL, 2), 2, bd("100"));
        executor.executeTrade(buy, order(21L, marketMaker, OrderSide.SELL, 3), 3, bd("200"));

        assertThat(buy.getExecutedQuantity()).isEqualTo(5);
        assertThat(buy.getRemainingQuantity()).isEqualTo(5);
        assertThat(buy.getAverageExecutedPrice()).isEqualByComparingTo("160.00");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(buyer.getBalance()).isEqualByComparingTo("9200");
    }

    @Test
    void selfTradeLocksUserOnceAndNetsCashWhileRewritingInventoryCost() {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setUser(buyer);
        holding.setSymbol("نماد");
        holding.setInstrumentCode("INS");
        holding.setQuantity(10L);
        holding.setBuyPrice(bd("80"));
        holding.setLivePrice(bd("100"));
        when(holdingRepository.findAllByUserIdAndInstrumentCode(1L, "INS"))
                .thenReturn(List.of(holding));
        when(holdingRepository.save(any(PortfolioHolding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TradingOrder buy = order(30L, buyer, OrderSide.BUY, 4);
        TradingOrder sell = order(31L, buyer, OrderSide.SELL, 4);

        Trade trade = executor.executeTrade(buy, sell, 4, bd("100"));

        assertThat(trade).isNotNull();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        // Cash nets to zero for a wash trade
        assertThat(buyer.getBalance()).isEqualByComparingTo("10000");
        verify(userRepository, times(1)).findByIdForUpdate(1L);
    }

    private User user(Long id, String username, String balance) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setBalance(bd(balance));
        return user;
    }

    private TradingOrder order(Long id, User user, OrderSide side, long quantity) {
        TradingOrder order = new TradingOrder();
        order.setId(id);
        order.setUser(user);
        order.setSide(side);
        order.setSymbol("نماد");
        order.setInstrumentCode("INS");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(bd("250"));
        order.setLivePrice(bd("150"));
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
