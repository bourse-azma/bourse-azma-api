package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.SupportRequestRatingRequest;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageRepository;
import com.ernoxin.bourseazmaapi.repository.SupportRequestRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import com.ernoxin.bourseazmaapi.service.supportrequest.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({
        SupportRequestServiceImpl.class,
        SupportRequestDtoMapper.class,
        SupportRequestMessageHandler.class,
        SupportRequestValidator.class,
        SupportRequestStateHandler.class,
        SupportRequestTextNormalizer.class,
        SupportRequestStatsLoader.class,
        SupportRequestQueryHelper.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SupportRequestConcurrencyIntegrationTest {

    @Autowired
    private SupportRequestService service;

    @Autowired
    private SupportRequestRepository requestRepository;

    @Autowired
    private SupportRequestMessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    private ExecutorService executor;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        if (executor != null) {
            executor.shutdownNow();
        }
        messageRepository.deleteAll();
        requestRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void simultaneousRatingsAreAcceptedExactlyOnce() throws Exception {
        User user = saveUser();
        SupportRequest ticket = saveClosedTicket(user);
        SupportRequestRatingRequest first = rating(4, "first");
        SupportRequestRatingRequest second = rating(5, "second");

        executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (SupportRequestRatingRequest rating : List.of(first, second)) {
            futures.add(executor.submit(() -> {
                authenticate(user);
                ready.countDown();
                start.await();
                try {
                    service.rateRequest(ticket.getId(), rating);
                    return null;
                } catch (Throwable error) {
                    return rootCause(error);
                } finally {
                    SecurityContextHolder.clearContext();
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

        assertThat(failures).singleElement().satisfies(failure ->
                assertThat(failure)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("قبلا امتیازدهی"));
        SupportRequest saved = requestRepository.findById(ticket.getId()).orElseThrow();
        assertThat(saved.getRating()).isIn(4, 5);
        assertThat(saved.getRatingComment()).isIn("first", "second");
    }

    private User saveUser() {
        User user = new User();
        user.setUsername("support-race");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        return userRepository.saveAndFlush(user);
    }

    private SupportRequest saveClosedTicket(User user) {
        SupportRequest ticket = new SupportRequest();
        ticket.setUser(user);
        ticket.setSubject("subject");
        ticket.setMessage("message");
        ticket.setCategory(SupportRequestCategory.OTHER);
        ticket.setPriority(SupportRequestPriority.MEDIUM);
        ticket.setStatus(SupportRequestStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        ticket.setClosedBy(SupportRequestClosedBy.USER);
        return requestRepository.saveAndFlush(ticket);
    }

    private SupportRequestRatingRequest rating(int value, String comment) {
        SupportRequestRatingRequest request = new SupportRequestRatingRequest();
        request.setRating(value);
        request.setComment(comment);
        return request;
    }

    private void authenticate(User user) {
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
