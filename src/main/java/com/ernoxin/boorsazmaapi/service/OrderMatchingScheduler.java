package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.config.OrderMatchingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMatchingScheduler {

    private final OrderMatchingService orderMatchingService;
    private final OrderMatchingProperties properties;

    @Scheduled(fixedDelayString = "${app.order-matching.poll-interval-ms:5000}")
    public void pollActiveOrders() {
        if (!properties.enabled()) {
            return;
        }

        try {
            int tradeCount = orderMatchingService.runMatchingForAllActiveInstruments().size();
            if (tradeCount > 0) {
                log.debug("Order matching scheduler executed {} trade(s).", tradeCount);
            }
        } catch (Exception ex) {
            log.warn("Order matching scheduler failed: {}", ex.getMessage());
        }
    }
}
