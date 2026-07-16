package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.OrderMatchingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMatchingScheduler {

    private final OrderMatchingService orderMatchingService;
    private final OrderTriggerService orderTriggerService;
    private final OrderMatchingProperties properties;
    private final MarketStateService marketStateService;
    private final TradingSessionLifecycleService sessionLifecycleService;
    private final AtomicReference<MarketSessionState> lastKnownState =
            new AtomicReference<>(MarketSessionState.UNKNOWN);

    @Scheduled(fixedDelayString = "${app.order-matching.poll-interval-ms:5000}")
    public void pollActiveOrders() {
        try {
            MarketSessionState currentState = marketStateService.getSessionState();
            if (currentState == MarketSessionState.UNKNOWN) {
                return;
            }
            MarketSessionState previousState = lastKnownState.get();
            if (currentState == MarketSessionState.CLOSED) {
                if (previousState != MarketSessionState.CLOSED) {
                    TradingSessionLifecycleService.SessionResetResult reset =
                            sessionLifecycleService.closeCurrentSession();
                    // Record CLOSED only after the transaction succeeds. A failed reset must
                    // be retried by the next poll instead of silently leaving daily orders open.
                    lastKnownState.set(MarketSessionState.CLOSED);
                    log.info("Trading session closed: expired {} order(s), cleared {} private liquidity level(s).",
                            reset.expiredOrders(), reset.clearedLiquidityLevels());
                } else {
                    lastKnownState.set(MarketSessionState.CLOSED);
                }
                return;
            }
            lastKnownState.set(MarketSessionState.OPEN);
            if (!properties.enabled()) {
                return;
            }

            int triggeredCount = orderTriggerService.evaluatePendingTriggers();
            if (triggeredCount > 0) {
                log.debug("Order trigger service activated {} conditional order(s).", triggeredCount);
            }
            int tradeCount = orderMatchingService.runMatchingForAllActiveInstruments().size();
            if (tradeCount > 0) {
                log.debug("Order matching scheduler executed {} trade(s).", tradeCount);
            }
        } catch (Exception ex) {
            log.warn("Order matching scheduler failed: {}", ex.getMessage());
        }
    }
}
