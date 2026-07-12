package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.OrderMatchingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMatchingScheduler {

    private final OrderMatchingService orderMatchingService;
    private final OrderTriggerService orderTriggerService;
    private final OrderMatchingProperties properties;
    private final MarketStateService marketStateService;

    @Value("${app.ui-debug-mode:false}")
    private boolean uiDebugMode;

    @Scheduled(fixedDelayString = "${app.order-matching.poll-interval-ms:5000}")
    public void pollActiveOrders() {
        if (!properties.enabled() || (!uiDebugMode && !marketStateService.isMarketOpen())) {
            return;
        }

        try {
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
