package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradeRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.OrderUpdateNotifier;
import com.ernoxin.bourseazmaapi.service.WalletLedgerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TradeExecutor.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradeExecutorConcurrencyIntegrationTest {

    @MockitoBean
    private OrderUpdateNotifier orderUpdateNotifier;

    @Autowired
    private TradeExecutor tradeExecutor;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TradingOrderRepository orderRepository;
    @Autowired
    private PortfolioHoldingRepository holdingRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private MarketMakerService marketMakerService;
    @MockitoBean
    private WalletLedgerService walletLedgerService;

    private ExecutorService executor;

    @AfterEach
    void cleanUp() {
        if (executor != null) {
            executor.shutdownNow();
        }
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @RepeatedTest(5)
    void simultaneousFirstBuysCreateOneHoldingAndAnExactWeightedAverage() throws Exception {
        User buyer = saveUser("concurrent-buyer", new BigDecimal("2000.00"));
        User marketMaker = saveUser(MarketMakerService.MARKET_MAKER_USERNAME, BigDecimal.ZERO);
        TradingOrder buyAtTen = saveOrder(buyer, OrderSide.BUY, 10L, "10.00");
        TradingOrder sellAtTen = saveOrder(marketMaker, OrderSide.SELL, 10L, "10.00");
        TradingOrder buyAtTwenty = saveOrder(buyer, OrderSide.BUY, 30L, "20.00");
        TradingOrder sellAtTwenty = saveOrder(marketMaker, OrderSide.SELL, 30L, "20.00");

        when(marketMakerService.isMarketMaker(any(User.class)))
                .thenAnswer(invocation -> MarketMakerService.MARKET_MAKER_USERNAME
                        .equals(invocation.getArgument(0, User.class).getUsername()));

        runTogether(
                () -> executeInTransaction(buyAtTen.getId(), sellAtTen.getId(), 10L, "10.00"),
                () -> executeInTransaction(buyAtTwenty.getId(), sellAtTwenty.getId(), 30L, "20.00")
        );

        List<PortfolioHolding> holdings = holdingRepository.findAllByUserIdOrderByAcquiredAtDesc(buyer.getId());
        assertThat(holdings).singleElement().satisfies(holding -> {
            assertThat(holding.getQuantity()).isEqualTo(40L);
            assertThat(holding.getBuyPrice()).isEqualByComparingTo("17.50");
            assertThat(holding.getInstrumentCode()).isEqualTo("IRO1TEST0001");
        });
        assertThat(userRepository.findById(buyer.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1300.00");
        assertThat(tradeRepository.count()).isEqualTo(2L);
        assertThat(orderRepository.findAllById(List.of(buyAtTen.getId(), buyAtTwenty.getId())))
                .allSatisfy(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
                    assertThat(order.getRemainingQuantity()).isZero();
                });
    }

    @RepeatedTest(5)
    void oppositeDirectionTradesCompleteWithoutDeadlockOrLostBalances() throws Exception {
        User firstUser = saveUser("first-trader", new BigDecimal("1000.00"));
        User secondUser = saveUser("second-trader", new BigDecimal("1000.00"));
        saveHolding(firstUser, 100L, "10.00");
        saveHolding(secondUser, 100L, "10.00");
        TradingOrder firstBuy = saveOrder(firstUser, OrderSide.BUY, 10L, "10.00");
        TradingOrder secondSell = saveOrder(secondUser, OrderSide.SELL, 10L, "10.00");
        TradingOrder secondBuy = saveOrder(secondUser, OrderSide.BUY, 10L, "10.00");
        TradingOrder firstSell = saveOrder(firstUser, OrderSide.SELL, 10L, "10.00");

        runTogether(
                () -> executeInTransaction(firstBuy.getId(), secondSell.getId(), 10L, "10.00"),
                () -> executeInTransaction(secondBuy.getId(), firstSell.getId(), 10L, "10.00")
        );

        assertThat(tradeRepository.count()).isEqualTo(2L);
        assertThat(userRepository.findById(firstUser.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1000.00");
        assertThat(userRepository.findById(secondUser.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1000.00");
        assertThat(holdingRepository.findAllByUserIdOrderByAcquiredAtDesc(firstUser.getId()))
                .singleElement().extracting(PortfolioHolding::getQuantity).isEqualTo(100L);
        assertThat(holdingRepository.findAllByUserIdOrderByAcquiredAtDesc(secondUser.getId()))
                .singleElement().extracting(PortfolioHolding::getQuantity).isEqualTo(100L);
    }

    @Test
    void databaseConstraintRejectsASecondHoldingForTheSameUserAndInstrument() {
        User user = saveUser("unique-holding", BigDecimal.ZERO);
        saveHolding(user, 10L, "10.00");

        assertThatThrownBy(() -> saveHolding(user, 5L, "20.00"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(holdingRepository.findAllByUserIdOrderByAcquiredAtDesc(user.getId()))
                .singleElement()
                .extracting(PortfolioHolding::getQuantity)
                .isEqualTo(10L);
    }

    private void executeInTransaction(Long buyOrderId, Long sellOrderId, long quantity, String price) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TradingOrder buy = orderRepository.findById(buyOrderId).orElseThrow();
            TradingOrder sell = orderRepository.findById(sellOrderId).orElseThrow();
            tradeExecutor.executeTrade(buy, sell, quantity, new BigDecimal(price));
        });
    }

    private void runTogether(Runnable first, Runnable second) throws Exception {
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> firstFuture = executor.submit(awaitStart(ready, start, first));
        Future<?> secondFuture = executor.submit(awaitStart(ready, start, second));
        ready.await();
        start.countDown();
        firstFuture.get(10, TimeUnit.SECONDS);
        secondFuture.get(10, TimeUnit.SECONDS);
    }

    private Runnable awaitStart(CountDownLatch ready, CountDownLatch start, Runnable action) {
        return () -> {
            ready.countDown();
            try {
                start.await();
                action.run();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        };
    }

    private User saveUser(String username, BigDecimal balance) {
        User user = new User();
        user.setUsername(username);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("hashed-password");
        user.setRole(UserRole.USER);
        user.setBalance(balance);
        return userRepository.saveAndFlush(user);
    }

    private TradingOrder saveOrder(User user, OrderSide side, long quantity, String price) {
        TradingOrder order = new TradingOrder();
        order.setUser(user);
        order.setSide(side);
        order.setSymbol("TEST");
        order.setInstrumentCode("IRO1TEST0001");
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(new BigDecimal(price));
        order.setLivePrice(new BigDecimal(price));
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        return orderRepository.saveAndFlush(order);
    }

    private PortfolioHolding saveHolding(User user, long quantity, String price) {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setUser(user);
        holding.setSymbol("TEST");
        holding.setInstrumentCode("IRO1TEST0001");
        holding.setQuantity(quantity);
        holding.setBuyPrice(new BigDecimal(price));
        holding.setLivePrice(new BigDecimal(price));
        holding.setAcquiredAt(Instant.now());
        return holdingRepository.saveAndFlush(holding);
    }
}
