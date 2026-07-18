package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserSelfUpdateRequest;
import com.ernoxin.bourseazmaapi.exception.InvalidCurrentPasswordException;
import com.ernoxin.bourseazmaapi.mapper.UserMapperImpl;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
class UserProfileConcurrencyIntegrationTest {

    @MockitoBean
    private OrderUpdateNotifier orderUpdateNotifier;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private ExecutorService executor;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        if (executor != null) {
            executor.shutdownNow();
        }
        userRepository.deleteAll();
    }

    @Test
    void simultaneousPasswordChangesWithSameOldPasswordCanSucceedOnlyOnce() throws Exception {
        User user = saveUser();
        when(passwordEncoder.matches("oldPassword1", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("oldPassword1", "newPassword1-hash")).thenReturn(false);
        when(passwordEncoder.matches("oldPassword1", "otherPassword2-hash")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> invocation.getArgument(0) + "-hash");

        executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (String newPassword : List.of("newPassword1", "otherPassword2")) {
            futures.add(executor.submit(() -> {
                authenticate(user);
                ready.countDown();
                start.await();
                try {
                    userService.updateCurrentUser(updateRequest(newPassword));
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

        assertThat(failures).singleElement().isInstanceOf(InvalidCurrentPasswordException.class);
        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getPassword()).isIn("newPassword1-hash", "otherPassword2-hash");
        assertThat(saved.getTokenVersion()).isEqualTo(1L);
        assertThat(saved.getUsername()).isEqualTo("profile-user");
    }

    private User saveUser() {
        User user = new User();
        user.setUsername("profile-user");
        user.setFirstName("ارفان");
        user.setLastName("آزما");
        user.setEmail("profile@example.com");
        user.setPassword("old-hash");
        user.setRole(UserRole.USER);
        return userRepository.saveAndFlush(user);
    }

    private UserSelfUpdateRequest updateRequest(String newPassword) {
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername(" PROFILE-USER ");
        request.setFirstName(" ارفان ");
        request.setLastName(" آزما ");
        request.setEmail(" PROFILE@EXAMPLE.COM ");
        request.setCurrentPassword("oldPassword1");
        request.setPassword(newPassword);
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
