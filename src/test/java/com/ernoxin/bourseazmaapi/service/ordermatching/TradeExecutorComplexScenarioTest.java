package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradeRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.WalletLedgerService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradeExecutorComplexScenarioTest {

    private TradingOrderRepository orderRepository;
    private TradeRepository tradeRepository;
    private PortfolioHoldingRepository holdingRepository;
    private UserRepository userRepository;
    private MarketMakerService marketMakerService;
    private WalletLedgerService walletLedgerService;
    private EntityManager entityManager;
    private TradeExecutor executor;

    @BeforeEach
    void setUp() {
        orderRepository = mock(TradingOrderRepository.class);
        tradeRepository = mock(TradeRepository.class);
        holdingRepository = mock(PortfolioHoldingRepository.class);
        userRepository = mock(UserRepository.class);
        marketMakerService = mock(MarketMakerService.class);
        walletLedgerService = mock(WalletLedgerService.class);
        entityManager = mock(EntityManager.class);
        executor = new TradeExecutor(
                orderRepository,
                tradeRepository,
                holdingRepository,
                userRepository,
                marketMakerService,
                walletLedgerService,
                entityManager
        );
        when(tradeRepository.save(any(Trade.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Trade.class));
    }

    @Test
    void partialFillMovesCashAndInventoryAndPreservesOrderInvariants() {
        User buyer = user(1L, "1000");
        User seller = user(2L, "0");
        PortfolioHolding sellerHolding = holding(seller, 100L, "1.00");
        TradingOrder buy = order(10L, buyer, OrderSide.BUY, 100L);
        TradingOrder sell = order(11L, seller, OrderSide.SELL, 40L);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(seller));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(2L, "IRO1TEST0001"))
                .thenReturn(List.of(sellerHolding));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(1L, "IRO1TEST0001"))
                .thenReturn(List.of());
        when(userRepository.getReferenceById(1L)).thenReturn(buyer);

        Trade trade = executor.executeTrade(buy, sell, 40L, new BigDecimal("2.00"));

        assertThat(trade.getValue()).isEqualByComparingTo("80.00");
        assertThat(buy.getExecutedQuantity()).isEqualTo(40L);
        assertThat(buy.getRemainingQuantity()).isEqualTo(60L);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(sell.getExecutedQuantity()).isEqualTo(40L);
        assertThat(sell.getRemainingQuantity()).isZero();
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(buyer.getBalance()).isEqualByComparingTo("920.00");
        assertThat(seller.getBalance()).isEqualByComparingTo("80.00");
        assertThat(sellerHolding.getQuantity()).isEqualTo(60L);

        verify(holdingRepository).save(argThat(created ->
                created.getUser().getId().equals(1L)
                        && created.getQuantity() == 40L
                        && created.getBuyPrice().compareTo(new BigDecimal("2.00")) == 0));
        verify(walletLedgerService).recordBalanceChange(eq(buyer), eq(new BigDecimal("-80.00")), anyString());
        verify(walletLedgerService).recordBalanceChange(eq(seller), eq(new BigDecimal("80.00")), anyString());
        verify(tradeRepository).save(any(Trade.class));
    }

    @Test
    void insufficientFundsFailsOnlyBuyLegWithoutCreatingATradeOrMovingInventory() {
        User buyer = user(1L, "50");
        User seller = user(2L, "0");
        TradingOrder buy = order(10L, buyer, OrderSide.BUY, 100L);
        TradingOrder sell = order(11L, seller, OrderSide.SELL, 100L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(seller));

        Trade trade = executor.executeTrade(buy, sell, 100L, BigDecimal.ONE);

        assertThat(trade).isNull();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(buy.getRemainingQuantity()).isZero();
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.REQUESTED);
        assertThat(sell.getRemainingQuantity()).isEqualTo(100L);
        assertThat(buyer.getBalance()).isEqualByComparingTo("50");
        verifyNoInteractions(tradeRepository, walletLedgerService);
        verifyNoInteractions(holdingRepository);
    }

    @Test
    void washTradeLedgerCapturesDebitThenCreditWhileNetBalanceAndSharesStayConstant() {
        User user = user(1L, "1000");
        PortfolioHolding existing = holding(user, 100L, "1.00");
        TradingOrder buy = order(10L, user, OrderSide.BUY, 20L);
        TradingOrder sell = order(11L, user, OrderSide.SELL, 20L);
        List<BigDecimal> balancesAtLedgerWrite = new ArrayList<>();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(1L, "IRO1TEST0001"))
                .thenReturn(List.of(existing), List.of(existing), List.of(existing));
        doAnswer(invocation -> {
            User ledgerUser = invocation.getArgument(0);
            balancesAtLedgerWrite.add(ledgerUser.getBalance());
            return null;
        }).when(walletLedgerService).recordBalanceChange(any(), any(), anyString());

        executor.executeTrade(buy, sell, 20L, new BigDecimal("2.00"));

        assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(balancesAtLedgerWrite).usingElementComparator(BigDecimal::compareTo).containsExactly(
                new BigDecimal("960.00"),
                new BigDecimal("1000.00")
        );
        assertThat(existing.getQuantity()).isEqualTo(100L);
        assertThat(existing.getBuyPrice()).isEqualByComparingTo("1.20");
        verify(holdingRepository, atLeastOnce()).save(existing);
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void accountLocksAlwaysFollowAscendingUserIdInsteadOfBuySellRole() {
        User buyer = user(9L, "1000");
        User seller = user(2L, "0");
        PortfolioHolding sellerHolding = holding(seller, 10L, "1.00");
        TradingOrder buy = order(10L, buyer, OrderSide.BUY, 10L);
        TradingOrder sell = order(11L, seller, OrderSide.SELL, 10L);

        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(seller));
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(buyer));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(2L, "IRO1TEST0001"))
                .thenReturn(List.of(sellerHolding));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(9L, "IRO1TEST0001"))
                .thenReturn(List.of());
        when(userRepository.getReferenceById(9L)).thenReturn(buyer);

        executor.executeTrade(buy, sell, 10L, BigDecimal.ONE);

        InOrder lockOrder = inOrder(userRepository);
        lockOrder.verify(userRepository).findByIdForUpdate(2L);
        lockOrder.verify(userRepository).findByIdForUpdate(9L);
    }

    @Test
    void legacyDuplicateHoldingsAreMergedWithTheirFullWeightedCostBasis() {
        User buyer = user(1L, "5000");
        User marketMaker = user(99L, "0");
        PortfolioHolding first = holding(buyer, 10L, "10.00");
        PortfolioHolding duplicate = holding(buyer, 30L, "20.00");
        TradingOrder buy = order(10L, buyer, OrderSide.BUY, 20L);
        TradingOrder sell = order(11L, marketMaker, OrderSide.SELL, 20L);

        when(marketMakerService.isMarketMaker(marketMaker)).thenReturn(true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        when(holdingRepository.findAllByUserIdAndInstrumentCodeForUpdate(1L, "IRO1TEST0001"))
                .thenReturn(List.of(first, duplicate));

        executor.executeTrade(buy, sell, 20L, new BigDecimal("40.00"));

        assertThat(first.getQuantity()).isEqualTo(60L);
        assertThat(first.getBuyPrice()).isEqualByComparingTo("25.00");
        assertThat(first.getLivePrice()).isEqualByComparingTo("40.00");
        verify(holdingRepository).deleteAll(List.of(duplicate));
        verify(userRepository, never()).getReferenceById(anyLong());
    }

    @Test
    void invalidExecutionIsRejectedBeforeAnyDatabaseMutation() {
        User buyer = user(1L, "1000");
        User seller = user(2L, "0");
        TradingOrder buy = order(10L, buyer, OrderSide.BUY, 10L);
        TradingOrder sell = order(11L, seller, OrderSide.SELL, 10L);

        assertThatThrownBy(() -> executor.executeTrade(buy, sell, 0L, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("تعداد");
        assertThatThrownBy(() -> executor.executeTrade(buy, sell, 1L, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("قیمت");
        sell.setInstrumentCode("OTHER");
        assertThatThrownBy(() -> executor.executeTrade(buy, sell, 1L, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("کد ابزار");

        verifyNoInteractions(userRepository, holdingRepository, orderRepository, tradeRepository,
                walletLedgerService, entityManager);
    }

    private User user(Long id, String balance) {
        User user = new User();
        user.setId(id);
        user.setBalance(new BigDecimal(balance));
        return user;
    }

    private PortfolioHolding holding(User user, long quantity, String price) {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setUser(user);
        holding.setSymbol("TEST");
        holding.setInstrumentCode("IRO1TEST0001");
        holding.setQuantity(quantity);
        holding.setBuyPrice(new BigDecimal(price));
        holding.setLivePrice(new BigDecimal(price));
        holding.setAcquiredAt(Instant.now());
        return holding;
    }

    private TradingOrder order(Long id, User user, OrderSide side, long quantity) {
        TradingOrder order = new TradingOrder();
        order.setId(id);
        order.setUser(user);
        order.setSide(side);
        order.setSymbol("TEST");
        order.setInstrumentCode("IRO1TEST0001");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(BigDecimal.ONE);
        order.setLivePrice(BigDecimal.ONE);
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        return order;
    }
}
