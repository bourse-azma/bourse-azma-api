package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TradingSessionLifecycleService.class)
class TradingSessionLifecycleIntegrationTest {

    @MockitoBean
    private OrderUpdateNotifier orderUpdateNotifier;

    @Autowired
    private TradingSessionLifecycleService lifecycleService;
    @Autowired
    private TradingOrderRepository orderRepository;
    @Autowired
    private UserLiquidityConsumptionRepository consumptionRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void closingSessionPersistsExpiredOrdersAndAnEmptyPrivateOverlay() {
        User user = new User();
        user.setUsername("daily-session-user");
        user.setFirstName("Daily");
        user.setLastName("Trader");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        user = userRepository.saveAndFlush(user);

        TradingOrder order = new TradingOrder();
        order.setUser(user);
        order.setSide(OrderSide.BUY);
        order.setSymbol("TEST");
        order.setInstrumentCode("IRO1TEST0001");
        order.setQuantity(100L);
        order.setRemainingQuantity(100L);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(new BigDecimal("100.00"));
        order.setLivePrice(new BigDecimal("100.00"));
        order.setOrderTime(Instant.now());
        order.setStatus(OrderStatus.REQUESTED);
        order.setOrderType(OrderType.NORMAL);
        order.setPriceType(PriceType.CUSTOM);
        order = orderRepository.saveAndFlush(order);

        UserLiquidityConsumption consumption = new UserLiquidityConsumption();
        consumption.setUserId(user.getId());
        consumption.setInstrumentCode(order.getInstrumentCode());
        consumption.setBookSide(BookSide.ASK);
        consumption.setPrice(new BigDecimal("100.00"));
        consumption.setConsumedQuantity(25L);
        consumption.setUpdatedAt(Instant.now());
        consumptionRepository.saveAndFlush(consumption);

        TradingSessionLifecycleService.SessionResetResult result = lifecycleService.closeCurrentSession();

        TradingOrder expired = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getRemainingQuantity()).isZero();
        assertThat(expired.getCancelledAt()).isNotNull();
        assertThat(consumptionRepository.count()).isZero();
        assertThat(result.expiredOrders()).isEqualTo(1);
        assertThat(result.clearedLiquidityLevels()).isEqualTo(1);
    }
}
