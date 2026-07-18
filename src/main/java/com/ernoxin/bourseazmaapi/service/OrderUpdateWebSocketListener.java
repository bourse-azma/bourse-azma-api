package com.ernoxin.bourseazmaapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderUpdateWebSocketListener {

    private final ClusterWebSocketPublisher clusterWebSocketPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderUpdate(OrderUpdateEvent event) {
        clusterWebSocketPublisher.sendToUser(event.username(), "/queue/orders", event.order());
    }
}
