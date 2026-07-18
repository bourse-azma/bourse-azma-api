package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.TradingOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderUpdateNotifier {

    private final ApplicationEventPublisher eventPublisher;
    private final TradingAccountResponseMapper responseMapper;

    public void publish(TradingOrder order) {
        if (order == null || order.getUser() == null || order.getUser().getUsername() == null) {
            return;
        }
        eventPublisher.publishEvent(new OrderUpdateEvent(
                order.getUser().getUsername(),
                responseMapper.toOrderResponse(order)
        ));
    }
}
