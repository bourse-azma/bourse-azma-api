package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.WatchlistCreateRequest;
import com.ernoxin.bourseazmaapi.exception.DuplicateResourceException;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WatchlistRepository;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(WatchlistServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WatchlistConcurrencyIntegrationTest {

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private UserRepository userRepository;

    private ExecutorService executor;

    @AfterEach
    void cleanUp() {
        if (executor != null) {
            executor.shutdownNow();
        }
        watchlistRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void simultaneousCaseVariantsCannotBypassCaseInsensitiveUniqueness() throws Exception {
        User user = saveUser();
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Throwable> first = executor.submit(() -> createAs(principal, "Portfolio", ready, start));
        Future<Throwable> second = executor.submit(() -> createAs(principal, "portfolio", ready, start));
        ready.await();
        start.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : List.of(first, second)) {
            Throwable failure = future.get();
            if (failure != null) {
                failures.add(failure);
            }
        }

        assertThat(failures).singleElement()
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(watchlistRepository.findDistinctByUserIdOrderByIdAsc(user.getId()))
                .singleElement()
                .satisfies(watchlist -> assertThat(watchlist.getName()).isEqualToIgnoringCase("portfolio"));
    }

    private Throwable createAs(AppUserPrincipal principal, String name,
                               CountDownLatch ready, CountDownLatch start) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        ready.countDown();
        try {
            start.await();
            WatchlistCreateRequest request = new WatchlistCreateRequest();
            request.setName(name);
            watchlistService.create(request);
            return null;
        } catch (Throwable error) {
            return rootCause(error);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User saveUser() {
        User user = new User();
        user.setUsername("watchlist-race");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        user.setBalance(BigDecimal.ZERO);
        return userRepository.saveAndFlush(user);
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
