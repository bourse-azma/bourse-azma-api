package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.mapper.UserMapper;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserAccountLifecycleTest {

    private UserRepository userRepository;
    private TradingOrderRepository orderRepository;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        orderRepository = mock(TradingOrderRepository.class);
        service = new UserServiceImpl(
                userRepository,
                mock(WalletTransactionRepository.class),
                orderRepository,
                mock(UserMapper.class),
                mock(PasswordEncoder.class)
        );
        authenticateAdmin(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminEndpointCannotDeleteAnotherAdministratorAccount() {
        User targetAdmin = user(2L, UserRole.ADMIN);
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void adminEndpointCannotBlockAnotherAdministratorAccount() {
        User targetAdmin = user(2L, UserRole.ADMIN);
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> service.setBlocked(2L, true, "test"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void deletingUserLocksAccountCancelsEveryReservableOrderAndRevokesTokens() {
        User target = user(7L, UserRole.USER);
        target.setUsername("target");
        target.setEmail("target@example.com");
        target.setPhoneNumber("09123456789");
        target.setPassword("old-hash");
        target.setTokenVersion(4L);
        TradingOrder requested = activeOrder(OrderStatus.REQUESTED, 12L);
        TradingOrder partial = activeOrder(OrderStatus.PARTIALLY_FILLED, 3L);
        TradingOrder conditional = activeOrder(OrderStatus.TRIGGER_PENDING, 9L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(target));
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));
        when(orderRepository.findAllByUserIdAndStatusIn(eq(7L), any()))
                .thenReturn(List.of(requested, partial, conditional));

        service.delete(7L);

        assertThat(List.of(requested, partial, conditional)).allSatisfy(order -> {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getRemainingQuantity()).isZero();
            assertThat(order.getCancelledAt()).isNotNull();
        });
        assertThat(target.getDeletedAt()).isNotNull();
        assertThat(target.isBlocked()).isTrue();
        assertThat(target.getTokenVersion()).isEqualTo(5L);
        assertThat(target.getUsername()).startsWith("deleted_7_");
        assertThat(target.getEmail()).isNull();
        assertThat(target.getPhoneNumber()).isNull();
        verify(userRepository).findByIdForUpdate(7L);
        verify(userRepository).save(target);
    }

    private TradingOrder activeOrder(OrderStatus status, long remaining) {
        TradingOrder order = new TradingOrder();
        order.setStatus(status);
        order.setRemainingQuantity(remaining);
        return order;
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("hash");
        user.setRole(role);
        return user;
    }

    private void authenticateAdmin(Long id) {
        User admin = user(id, UserRole.ADMIN);
        AppUserPrincipal principal = AppUserPrincipal.from(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
