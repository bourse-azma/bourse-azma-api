package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.OrderMatchingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class OrderMatchingSchedulerTest {

    private OrderMatchingService matchingService;
    private OrderTriggerService triggerService;
    private MarketStateService marketStateService;
    private TradingSessionLifecycleService lifecycleService;
    private OrderMatchingScheduler scheduler;

    @BeforeEach
    void setUp() {
        matchingService = mock(OrderMatchingService.class);
        triggerService = mock(OrderTriggerService.class);
        marketStateService = mock(MarketStateService.class);
        lifecycleService = mock(TradingSessionLifecycleService.class);
        when(matchingService.runMatchingForAllActiveInstruments()).thenReturn(List.of());
        when(lifecycleService.closeCurrentSession()).thenReturn(
                new TradingSessionLifecycleService.SessionResetResult(2, 3));
        scheduler = new OrderMatchingScheduler(
                matchingService,
                triggerService,
                new OrderMatchingProperties(5_000, true),
                marketStateService,
                lifecycleService
        );
    }

    @Test
    void keepsMatchingWhileOpenAndClosesTheSessionExactlyOnceOnTransition() {
        when(marketStateService.getSessionState()).thenReturn(
                MarketSessionState.OPEN,
                MarketSessionState.OPEN,
                MarketSessionState.CLOSED,
                MarketSessionState.CLOSED
        );

        scheduler.pollActiveOrders();
        scheduler.pollActiveOrders();
        scheduler.pollActiveOrders();
        scheduler.pollActiveOrders();

        verify(matchingService, times(2)).runMatchingForAllActiveInstruments();
        verify(triggerService, times(2)).evaluatePendingTriggers();
        verify(lifecycleService, times(1)).closeCurrentSession();
    }

    @Test
    void aProcessStartingAfterMarketCloseStillResetsTheDailyBook() {
        when(marketStateService.getSessionState()).thenReturn(
                MarketSessionState.CLOSED,
                MarketSessionState.CLOSED
        );

        scheduler.pollActiveOrders();
        scheduler.pollActiveOrders();

        verify(lifecycleService, times(1)).closeCurrentSession();
        verifyNoInteractions(triggerService);
        verify(matchingService, never()).runMatchingForAllActiveInstruments();
    }

    @Test
    void anUnknownApiStateDoesNotCloseOrForgetAnObservedOpenSession() {
        when(marketStateService.getSessionState()).thenReturn(
                MarketSessionState.OPEN,
                MarketSessionState.UNKNOWN,
                MarketSessionState.CLOSED
        );

        scheduler.pollActiveOrders();
        scheduler.pollActiveOrders();
        verify(lifecycleService, never()).closeCurrentSession();
        scheduler.pollActiveOrders();

        verify(lifecycleService).closeCurrentSession();
    }

    @Test
    void retriesSessionCloseAfterATransientResetFailure() {
        when(marketStateService.getSessionState()).thenReturn(
                MarketSessionState.OPEN,
                MarketSessionState.CLOSED,
                MarketSessionState.CLOSED
        );
        when(lifecycleService.closeCurrentSession())
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(new TradingSessionLifecycleService.SessionResetResult(1, 1));

        scheduler.pollActiveOrders();
        assertThatCode(scheduler::pollActiveOrders).doesNotThrowAnyException();
        scheduler.pollActiveOrders();

        verify(lifecycleService, times(2)).closeCurrentSession();
    }
}
