package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.CreateTradingOrderRequest;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.trading.minimum-order-value=1",
        "app.trading.maximum-wallet-adjustment=1000000",
        "app.ui-debug-mode=true"
})
@Import({TradingAccountServiceImpl.class, TradingAccountResponseMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradingConcurrencyIntegrationTest {

    @Autowired
    private TradingAccountService tradingAccountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TradingOrderRepository orderRepository;

    @Autowired
    private PortfolioHoldingRepository holdingRepository;

    @MockitoBean
    private OrderMatchingService orderMatchingService;

    @MockitoBean
    private MarketLiquidityService marketLiquidityService;

    @MockitoBean
    private MarketStateService marketStateService;

    @MockitoBean
    private PrivateOrderBookService privateOrderBookService;

    private ExecutorService executor;

    @AfterEach
    void cleanUp() {
        if (executor != null) {
            executor.shutdownNow();
        }
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void simultaneousBuyRequestsCannotReserveMoreThanTheWalletBalance() throws Exception {
        User user = saveUser("buy-race", new BigDecimal("100"));
        when(marketStateService.isMarketOpen()).thenReturn(false);

        List<Throwable> failures = runConcurrently(2, () ->
                tradingAccountService.createOrder(user.getId(), buyRequest("60")));

        assertThat(failures).hasSize(1)
                .allSatisfy(error -> assertThat(rootCause(error))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("قدرت خرید"));
        assertThat(orderRepository.findAll()).singleElement()
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.REQUESTED);
                    assertThat(order.getOrderPrice().multiply(BigDecimal.valueOf(order.getRemainingQuantity())))
                            .isEqualByComparingTo("60.00");
                });
    }

    @Test
    void simultaneousSellRequestsCannotReserveTheSameSharesTwice() throws Exception {
        User user = saveUser("sell-race", BigDecimal.ZERO);
        saveHolding(user, 100L);
        when(marketStateService.isMarketOpen()).thenReturn(false);

        List<Throwable> failures = runConcurrently(2, () ->
                tradingAccountService.createOrder(user.getId(), sellRequest(60L)));

        assertThat(failures).hasSize(1)
                .allSatisfy(error -> assertThat(rootCause(error))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("40"));
        assertThat(orderRepository.findAll()).singleElement()
                .extracting(TradingOrder::getRemainingQuantity)
                .isEqualTo(60L);
        assertThat(holdingRepository.findAll()).singleElement()
                .extracting(PortfolioHolding::getQuantity)
                .isEqualTo(100L);
    }

    @Test
    void simultaneousCancellationIsAppliedExactlyOnce() throws Exception {
        User user = saveUser("cancel-race", new BigDecimal("100"));
        TradingOrder order = saveActiveBuyOrder(user);

        List<Throwable> failures = runConcurrently(2, () ->
                tradingAccountService.cancelOrder(user.getId(), order.getId()));

        assertThat(failures).hasSize(1)
                .allSatisfy(error -> assertThat(rootCause(error))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("قبلاً لغو"));
        TradingOrder cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getRemainingQuantity()).isZero();
        assertThat(cancelled.getCancelledAt()).isNotNull();
    }

    private List<Throwable> runConcurrently(int taskCount, ThrowingAction action) throws Exception {
        executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (int index = 0; index < taskCount; index++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    action.run();
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            }));
        }
        ready.await();
        start.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            Throwable failure = future.get();
            if (failure != null) {
                failures.add(failure);
            }
        }
        return failures;
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

    private void saveHolding(User user, long quantity) {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setUser(user);
        holding.setSymbol("TEST");
        holding.setInstrumentCode("IRO1TEST0001");
        holding.setQuantity(quantity);
        holding.setBuyPrice(BigDecimal.ONE);
        holding.setLivePrice(BigDecimal.ONE);
        holding.setAcquiredAt(Instant.now());
        holdingRepository.saveAndFlush(holding);
    }

    private TradingOrder saveActiveBuyOrder(User user) {
        TradingOrder order = new TradingOrder();
        order.setUser(user);
        order.setSide(OrderSide.BUY);
        order.setSymbol("TEST");
        order.setInstrumentCode("IRO1TEST0001");
        order.setQuantity(20L);
        order.setRemainingQuantity(20L);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(new BigDecimal("2.00"));
        order.setLivePrice(new BigDecimal("2.00"));
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        return orderRepository.saveAndFlush(order);
    }

    private CreateTradingOrderRequest buyRequest(String price) {
        return orderRequest(OrderSide.BUY, 1L, price);
    }

    private CreateTradingOrderRequest sellRequest(long quantity) {
        return orderRequest(OrderSide.SELL, quantity, "1");
    }

    private CreateTradingOrderRequest orderRequest(OrderSide side, long quantity, String price) {
        CreateTradingOrderRequest request = new CreateTradingOrderRequest();
        request.setSide(side);
        request.setOrderType(OrderType.NORMAL);
        request.setPriceType(PriceType.CUSTOM);
        request.setSymbol("TEST");
        request.setInstrumentCode("IRO1TEST0001");
        request.setQuantity(quantity);
        request.setPrice(new BigDecimal(price));
        request.setLivePrice(new BigDecimal(price));
        return request;
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

}
