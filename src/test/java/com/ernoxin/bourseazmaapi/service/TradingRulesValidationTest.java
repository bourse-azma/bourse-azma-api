package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.TradingRulesProperties;
import com.ernoxin.bourseazmaapi.dto.CreateTradingOrderRequest;
import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.mapper.UserMapper;
import com.ernoxin.bourseazmaapi.mapper.WalletMapper;
import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderType;
import com.ernoxin.bourseazmaapi.model.PriceType;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradingRulesValidationTest {

    private static final TradingRulesProperties RULES = new TradingRulesProperties(
            new BigDecimal("5000000"),
            new BigDecimal("1000000000000")
    );

    @Test
    void rejectsOrderBelowConfiguredMinimumValue() {
        TradingOrderRepository orderRepository = mock(TradingOrderRepository.class);
        PortfolioHoldingRepository holdingRepository = mock(PortfolioHoldingRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrderMatchingService matchingService = mock(OrderMatchingService.class);
        MarketLiquidityService liquidityService = mock(MarketLiquidityService.class);
        MarketStateService marketStateService = mock(MarketStateService.class);
        TradingAccountResponseMapper responseMapper = mock(TradingAccountResponseMapper.class);
        PrivateOrderBookService orderBookService = mock(PrivateOrderBookService.class);

        User user = new User();
        user.setBalance(new BigDecimal("100000000"));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(marketStateService.isMarketOpen()).thenReturn(true);

        TradingAccountServiceImpl service = new TradingAccountServiceImpl(
                orderRepository,
                holdingRepository,
                userRepository,
                matchingService,
                liquidityService,
                marketStateService,
                responseMapper,
                orderBookService,
                RULES
        );

        CreateTradingOrderRequest request = new CreateTradingOrderRequest();
        request.setSide(OrderSide.BUY);
        request.setOrderType(OrderType.NORMAL);
        request.setPriceType(PriceType.CUSTOM);
        request.setSymbol("فولاد");
        request.setInstrumentCode("123");
        request.setQuantity(4L);
        request.setPrice(new BigDecimal("1000000"));
        request.setLivePrice(new BigDecimal("1000000"));

        assertThatThrownBy(() -> service.createOrder(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5,000,000");
    }

    @Test
    void rejectsWalletAdjustmentAboveConfiguredMaximum() {
        UserRepository userRepository = mock(UserRepository.class);
        WalletTransactionRepository walletRepository = mock(WalletTransactionRepository.class);
        UserMapper userMapper = mock(UserMapper.class);
        WalletMapper walletMapper = mock(WalletMapper.class);
        TradingOrderRepository orderRepository = mock(TradingOrderRepository.class);

        User user = new User();
        user.setBalance(BigDecimal.ZERO);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        WalletServiceImpl service = new WalletServiceImpl(
                userRepository,
                walletRepository,
                userMapper,
                walletMapper,
                orderRepository,
                RULES
        );

        WalletAdjustmentRequest request = new WalletAdjustmentRequest();
        request.setType("ADD");
        request.setValue(new BigDecimal("999999999999999999999999999999"));

        assertThatThrownBy(() -> service.adjustBalance(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1,000,000,000,000");
    }
}
