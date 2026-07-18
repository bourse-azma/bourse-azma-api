package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.mapper.UserMapper;
import com.ernoxin.bourseazmaapi.mapper.WalletMapper;
import com.ernoxin.bourseazmaapi.mapper.WalletMapperImpl;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.trading.minimum-order-value=1",
        "app.trading.maximum-wallet-adjustment=1000000"
})
@Import({WalletServiceImpl.class, WalletMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletConcurrencyIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Autowired
    private TradingOrderRepository orderRepository;

    @MockitoBean
    private UserMapper userMapper;

    @Autowired
    private WalletMapper walletMapper;

    private ExecutorService executor;

    @AfterEach
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
        transactionRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentDepositsDoNotLoseUpdatesAndEveryLedgerSnapshotIsConsistent() throws Exception {
        User user = saveUser("deposit-user", BigDecimal.ZERO);

        List<Throwable> failures = runConcurrently(24, () ->
                walletService.adjustBalance(user.getId(), adjustment("ADD", "10")));

        assertThat(failures).isEmpty();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("240.00");
        assertThat(transactionRepository.findAll())
                .hasSize(24)
                .extracting(tx -> tx.getBalanceAfter().stripTrailingZeros())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.LongStream.rangeClosed(1, 24)
                                .mapToObj(value -> BigDecimal.valueOf(value * 10).stripTrailingZeros())
                                .toList()
                );
    }

    @Test
    void concurrentWithdrawalsNeverMakeBalanceNegative() throws Exception {
        User user = saveUser("withdraw-user", new BigDecimal("100"));

        List<Throwable> failures = runConcurrently(24, () ->
                walletService.adjustBalance(user.getId(), adjustment("SUBTRACT", "10")));

        assertThat(failures).hasSize(14)
                .allSatisfy(error -> assertThat(rootCause(error))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("بیشتر"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
        assertThat(transactionRepository.findAll())
                .hasSize(10)
                .allSatisfy(tx -> assertThat(tx.getBalanceAfter()).isNotNegative());
    }

    @Test
    void concurrentWithdrawalsRecalculateBuyingPowerAfterEachCommittedRequest() throws Exception {
        User user = saveUser("reserved-balance-user", new BigDecimal("100"));
        saveReservedBuyOrder(user, new BigDecimal("70"));

        List<Throwable> failures = runConcurrently(2, () ->
                walletService.adjustBalance(user.getId(), adjustment("SUBTRACT", "20")));

        assertThat(failures).singleElement()
                .satisfies(error -> assertThat(rootCause(error))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("بلوکه"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("80.00");
        assertThat(transactionRepository.count()).isEqualTo(1L);
    }

    @Test
    void ledgerInsertFailureRollsBackTheWalletMutationAtomically() {
        User user = saveUser("rollback-user", new BigDecimal("100"));
        WalletAdjustmentRequest request = adjustment("ADD", "50");
        request.setDescription("x".repeat(300));

        assertThatThrownBy(() -> walletService.adjustBalance(user.getId(), request))
                .isInstanceOf(RuntimeException.class);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void transactionHistoryFetchesTheAdminRelationBeforeMappingItsName() {
        User user = saveUser("wallet-history-user", BigDecimal.ZERO);
        User admin = saveUser("wallet-history-admin", BigDecimal.ZERO);
        admin.setFirstName("Admin");
        admin.setLastName("Operator");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.saveAndFlush(admin);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(user);
        transaction.setAmount(BigDecimal.TEN);
        transaction.setBalanceAfter(BigDecimal.TEN);
        transaction.setDescription("افزایش موجودی");
        transaction.setPerformedByAdmin(admin);
        transaction.setCreatedAt(Instant.now());
        transactionRepository.saveAndFlush(transaction);
        Long adminId = admin.getId();

        PagedResponse<WalletTransactionResponse> history = walletService.getTransactions(user.getId(), 0, 20);

        assertThat(history.items()).singleElement().satisfies(item -> {
            assertThat(item.getPerformedByAdminId()).isEqualTo(adminId);
            assertThat(item.getPerformedByAdminName()).isEqualTo("Admin Operator");
        });
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

    private WalletAdjustmentRequest adjustment(String type, String value) {
        WalletAdjustmentRequest request = new WalletAdjustmentRequest();
        request.setType(type);
        request.setValue(new BigDecimal(value));
        return request;
    }

    private void saveReservedBuyOrder(User user, BigDecimal value) {
        TradingOrder order = new TradingOrder();
        order.setUser(user);
        order.setSide(OrderSide.BUY);
        order.setSymbol("TEST");
        order.setInstrumentCode("IRO1TEST0001");
        order.setQuantity(1L);
        order.setRemainingQuantity(1L);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(value);
        order.setLivePrice(value);
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        orderRepository.saveAndFlush(order);
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
