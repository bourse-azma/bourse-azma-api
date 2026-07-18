package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TradingSessionLifecycleServiceTest {

    private TradingOrderRepository orderRepository;
    private UserLiquidityConsumptionRepository consumptionRepository;
    private TradingSessionLifecycleService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(TradingOrderRepository.class);
        consumptionRepository = mock(UserLiquidityConsumptionRepository.class);
        service = new TradingSessionLifecycleService(
                orderRepository, consumptionRepository, mock(OrderUpdateNotifier.class));
    }

    @Test
    void closeExpiresEveryDailyOrderAndClearsAllPrivateMarketEffects() {
        when(orderRepository.expireAllActiveOrders(any(), any())).thenReturn(4);
        when(consumptionRepository.deleteAllForSessionReset()).thenReturn(7);

        TradingSessionLifecycleService.SessionResetResult result = service.closeCurrentSession();

        ArgumentCaptor<List<OrderStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).expireAllActiveOrders(statuses.capture(), any(Instant.class));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED, OrderStatus.TRIGGER_PENDING);
        verify(consumptionRepository).deleteAllForSessionReset();
        assertThat(result.expiredOrders()).isEqualTo(4);
        assertThat(result.clearedLiquidityLevels()).isEqualTo(7);
    }

    @Test
    void startupSafetyNetExpiresOnlyOrdersFromPreviousDays() {
        Instant cutoff = Instant.parse("2026-07-15T20:30:00Z");
        when(orderRepository.expireActiveOrdersBefore(any(), eq(cutoff), any())).thenReturn(2);

        assertThat(service.expireOrdersBefore(cutoff)).isEqualTo(2);

        verify(orderRepository).expireActiveOrdersBefore(any(), eq(cutoff), any(Instant.class));
        verifyNoInteractions(consumptionRepository);
    }
}
