package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.model.OrderSide;
import com.ernoxin.boorsazmaapi.model.OrderStatus;
import com.ernoxin.boorsazmaapi.model.OrderType;
import com.ernoxin.boorsazmaapi.model.OrderValidity;
import com.ernoxin.boorsazmaapi.model.PriceType;
import com.ernoxin.boorsazmaapi.model.TradingOrder;
import com.ernoxin.boorsazmaapi.model.User;
import com.ernoxin.boorsazmaapi.repository.TradingOrderRepository;
import com.ernoxin.boorsazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MarketMakerService {

    public static final String MARKET_MAKER_USERNAME = "__market_maker__";

    private final UserRepository userRepository;
    private final TradingOrderRepository tradingOrderRepository;

    public User getMarketMaker() {
        return userRepository.findByUsername(MARKET_MAKER_USERNAME)
                .orElseThrow(() -> new IllegalStateException("Market maker user is not initialized."));
    }

    public boolean isMarketMaker(User user) {
        return user != null && MARKET_MAKER_USERNAME.equals(user.getUsername());
    }

    public TradingOrder createCompletedCounterOrder(TradingOrder userOrder, OrderSide counterSide,
                                                    long quantity, BigDecimal price) {
        User marketMaker = getMarketMaker();
        Instant now = Instant.now();

        TradingOrder counterOrder = new TradingOrder();
        counterOrder.setUser(marketMaker);
        counterOrder.setSide(counterSide);
        counterOrder.setOrderType(OrderType.NORMAL);
        counterOrder.setPriceType(PriceType.CUSTOM);
        counterOrder.setSymbol(userOrder.getSymbol());
        counterOrder.setInstrumentCode(userOrder.getInstrumentCode());
        counterOrder.setQuantity(quantity);
        counterOrder.setRemainingQuantity(quantity);
        counterOrder.setExecutedQuantity(0L);
        counterOrder.setOrderPrice(price);
        counterOrder.setLivePrice(userOrder.getLivePrice());
        counterOrder.setAverageExecutedPrice(null);
        counterOrder.setOrderTime(now);
        counterOrder.setStatus(OrderStatus.REQUESTED);
        counterOrder.setValidity(OrderValidity.TODAY);
        counterOrder.setExpiresAt(now);

        return tradingOrderRepository.save(counterOrder);
    }
}
