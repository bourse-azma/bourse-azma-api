package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class TradingOrderRepositoryIsolationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TradingOrderRepository orderRepository;

    @Test
    void privateBookQueryNeverReturnsAnotherUsersOrders() {
        User first = userRepository.save(user("first_user"));
        User second = userRepository.save(user("second_user"));
        TradingOrder firstOrder = orderRepository.save(order(first, "INS", OrderSide.BUY, "100"));
        orderRepository.save(order(second, "INS", OrderSide.SELL, "99"));
        orderRepository.save(order(first, "OTHER", OrderSide.SELL, "99"));

        List<TradingOrder> result = orderRepository.findActiveOrdersForPrivateBook(
                first.getId(), "INS", List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED));

        assertThat(result).extracting(TradingOrder::getId).containsExactly(firstOrder.getId());
        assertThat(result).allMatch(found -> found.getUser().getId().equals(first.getId()));
    }

    @Test
    void inactiveAndConditionalOrdersAreNotVisibleAsRestingLiquidity() {
        User user = userRepository.save(user("queue_user"));
        TradingOrder completed = order(user, "INS", OrderSide.BUY, "100");
        completed.setStatus(OrderStatus.COMPLETED);
        completed.setRemainingQuantity(0L);
        orderRepository.save(completed);
        TradingOrder conditional = order(user, "INS", OrderSide.BUY, "98");
        conditional.setStatus(OrderStatus.TRIGGER_PENDING);
        orderRepository.save(conditional);

        List<TradingOrder> result = orderRepository.findActiveOrdersForPrivateBook(
                user.getId(), "INS", List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED));

        assertThat(result).isEmpty();
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("encoded-password");
        user.setRole(UserRole.USER);
        user.setBalance(new BigDecimal("1000000"));
        return user;
    }

    private TradingOrder order(User user, String instrumentCode, OrderSide side, String price) {
        TradingOrder order = new TradingOrder();
        order.setUser(user);
        order.setSide(side);
        order.setSymbol("نماد");
        order.setInstrumentCode(instrumentCode);
        order.setQuantity(10L);
        order.setRemainingQuantity(10L);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(new BigDecimal(price));
        order.setLivePrice(new BigDecimal("100"));
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        return order;
    }
}
