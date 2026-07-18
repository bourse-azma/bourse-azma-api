package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserCreateRequest;
import com.ernoxin.bourseazmaapi.mapper.UserMapperImpl;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({UserServiceImpl.class, UserMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserRegistrationConcurrencyIntegrationTest {

    @MockitoBean
    private OrderUpdateNotifier orderUpdateNotifier;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletTransactionRepository walletRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private ExecutorService executor;

    @AfterEach
    void cleanUp() {
        if (executor != null) {
            executor.shutdownNow();
        }
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void simultaneousCaseVariantsCreateExactlyOneUserAndOneAtomicOpeningLedgerEntry() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Throwable>> futures = new ArrayList<>();
        for (String username : List.of("  Race.User  ", "race.user")) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    userService.create(request(username));
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

        assertThat(failures).hasSize(1);
        assertThat(userRepository.findAll()).singleElement().satisfies(user -> {
            assertThat(user.getUsername()).isEqualTo("race.user");
            assertThat(user.getEmail()).isEqualTo("race@example.com");
            assertThat(user.getPassword()).isEqualTo("encoded-password");
            assertThat(user.getBalance()).isEqualByComparingTo("1250.50");
        });
        assertThat(walletRepository.findAll()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getAmount()).isEqualByComparingTo("1250.50");
            assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("1250.50");
        });
    }

    private UserCreateRequest request(String username) {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setFirstName("  ارفان  ");
        request.setLastName("  آزما  ");
        request.setEmail("  RACE@Example.COM ");
        request.setPhoneNumber(null);
        request.setPassword("password1");
        request.setBalance(new BigDecimal("1250.50"));
        return request;
    }
}
