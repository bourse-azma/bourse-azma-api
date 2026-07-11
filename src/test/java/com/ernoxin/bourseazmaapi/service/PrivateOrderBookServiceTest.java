package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.PrivateOrderBookResponse;
import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.ResidualBookLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateOrderBookServiceTest {

    @Mock
    private TradingOrderRepository orderRepository;
    @Mock
    private PrivateBookStateService privateBookStateService;

    private PrivateOrderBookService service;

    @BeforeEach
    void setUp() {
        service = new PrivateOrderBookService(orderRepository, privateBookStateService);
    }

    @Test
    void overlaysOwnOrdersOnResidualPublicDepthNotRawMarket() {
        when(privateBookStateService.loadResidualBidLevels(41L, "INS-1")).thenReturn(List.of(
                new ResidualBookLevel(bd("100"), 1_000, 4, 900),
                new ResidualBookLevel(bd("99"), 500, 2, 500)
        ));
        when(privateBookStateService.loadResidualAskLevels(41L, "INS-1")).thenReturn(List.of(
                new ResidualBookLevel(bd("102"), 700, 3, 700)
        ));
        when(orderRepository.findActiveOrdersForPrivateBook(
                41L, "INS-1", List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED)))
                .thenReturn(List.of(
                        order(OrderSide.BUY, "100", 25),
                        order(OrderSide.BUY, "101", 10),
                        order(OrderSide.SELL, "102", 30)
                ));

        PrivateOrderBookResponse result = service.getOrderBook(41L, " INS-1 ");

        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows().get(0).bidPrice()).isEqualByComparingTo("101");
        assertThat(result.rows().get(0).bidVolume()).isEqualTo(10);
        assertThat(result.rows().get(0).ownBidVolume()).isEqualTo(10);
        assertThat(result.rows().get(1).bidPrice()).isEqualByComparingTo("100");
        // residual 900 + own 25
        assertThat(result.rows().get(1).bidVolume()).isEqualTo(925);
        assertThat(result.rows().get(1).ownBidVolume()).isEqualTo(25);
        assertThat(result.rows().get(0).askPrice()).isEqualByComparingTo("102");
        assertThat(result.rows().get(0).askVolume()).isEqualTo(730);
        assertThat(result.rows().get(0).ownAskVolume()).isEqualTo(30);
        verify(orderRepository).findActiveOrdersForPrivateBook(
                41L, "INS-1", List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED));
    }

    @Test
    void rejectsBlankInstrumentCodeBeforeAccessingSharedData() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getOrderBook(1L, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TradingOrder order(OrderSide side, String price, long remaining) {
        TradingOrder order = new TradingOrder();
        order.setSide(side);
        order.setOrderPrice(bd(price));
        order.setRemainingQuantity(remaining);
        return order;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
