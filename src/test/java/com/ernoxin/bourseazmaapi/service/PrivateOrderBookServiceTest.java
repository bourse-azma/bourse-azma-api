package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.PrivateOrderBookLevelResponse;
import com.ernoxin.bourseazmaapi.dto.PrivateOrderBookResponse;
import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.ResidualBookLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateOrderBookServiceTest {

    private TradingOrderRepository orderRepository;
    private PrivateBookStateService bookStateService;
    private PrivateOrderBookService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(TradingOrderRepository.class);
        bookStateService = mock(PrivateBookStateService.class);
        service = new PrivateOrderBookService(orderRepository, bookStateService);

        when(bookStateService.loadResidualBidLevels(1L, "CODE")).thenReturn(List.of(
                residual("100", 1_000, 10), residual("99", 900, 9),
                residual("98", 800, 8), residual("97", 700, 7), residual("96", 600, 6)
        ));
        when(bookStateService.loadResidualAskLevels(1L, "CODE")).thenReturn(List.of(
                residual("101", 1_000, 10), residual("102", 900, 9),
                residual("103", 800, 8), residual("104", 700, 7), residual("105", 600, 6)
        ));
    }

    @Test
    void reservesADisplaySlotForAnOwnBuyFarBelowThePublicTopFive() {
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of(order(OrderSide.BUY, "1", 2_500)));

        PrivateOrderBookResponse response = service.getOrderBook(1L, " CODE ");

        assertThat(response.rows()).hasSize(5);
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::bidPrice)
                .containsExactly(new BigDecimal("100"), new BigDecimal("99"), new BigDecimal("98"),
                        new BigDecimal("97"), new BigDecimal("1.00"));
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::ownBidVolume)
                .containsExactly(0L, 0L, 0L, 0L, 2_500L);
        assertThat(response.rows().get(4).bidVolume()).isEqualTo(2_500);
        assertThat(response.rows().get(4).bidOrderCount()).isEqualTo(1);
    }

    @Test
    void reservesADisplaySlotForAnOwnSellFarAboveThePublicTopFive() {
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of(order(OrderSide.SELL, "1000", 3_000)));

        PrivateOrderBookResponse response = service.getOrderBook(1L, "CODE");

        assertThat(response.rows()).hasSize(5);
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::askPrice)
                .containsExactly(new BigDecimal("101"), new BigDecimal("102"), new BigDecimal("103"),
                        new BigDecimal("104"), new BigDecimal("1000.00"));
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::ownAskVolume)
                .containsExactly(0L, 0L, 0L, 0L, 3_000L);
        assertThat(response.rows().get(4).askVolume()).isEqualTo(3_000);
        assertThat(response.rows().get(4).askOrderCount()).isEqualTo(1);
    }

    @Test
    void mergesOwnOrderIntoAnExistingPublicLevelWithoutDuplicatingIt() {
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of(order(OrderSide.BUY, "99.00", 250)));

        PrivateOrderBookResponse response = service.getOrderBook(1L, "CODE");

        assertThat(response.rows()).hasSize(5);
        PrivateOrderBookLevelResponse mergedLevel = response.rows().get(1);
        assertThat(mergedLevel.bidPrice()).isEqualByComparingTo("99");
        assertThat(mergedLevel.bidVolume()).isEqualTo(1_150);
        assertThat(mergedLevel.bidOrderCount()).isEqualTo(10);
        assertThat(mergedLevel.ownBidVolume()).isEqualTo(250);
    }

    @Test
    void insertsBetterOwnPricesAndDisplacesTheWorstPublicLevelsOnBothSides() {
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of(
                        order(OrderSide.BUY, "100.50", 20),
                        order(OrderSide.SELL, "100.75", 30)
                ));

        PrivateOrderBookResponse response = service.getOrderBook(1L, "CODE");

        assertThat(response.rows()).hasSize(5);
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::bidPrice)
                .containsExactly(new BigDecimal("100.50"), new BigDecimal("100"), new BigDecimal("99"),
                        new BigDecimal("98"), new BigDecimal("97"));
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::askPrice)
                .containsExactly(new BigDecimal("100.75"), new BigDecimal("101"), new BigDecimal("102"),
                        new BigDecimal("103"), new BigDecimal("104"));
        assertThat(response.rows().getFirst().ownBidVolume()).isEqualTo(20);
        assertThat(response.rows().getFirst().ownAskVolume()).isEqualTo(30);
        assertThat(response.rows().getFirst().bidOrderCount()).isEqualTo(1);
        assertThat(response.rows().getFirst().askOrderCount()).isEqualTo(1);
    }

    @Test
    void aggregatesMultipleOwnOrdersAtTheirExactSharedPrice() {
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of(
                        order(OrderSide.BUY, "99", 100),
                        order(OrderSide.BUY, "99.00", 150)
                ));

        PrivateOrderBookResponse response = service.getOrderBook(1L, "CODE");

        PrivateOrderBookLevelResponse level = response.rows().get(1);
        assertThat(level.bidPrice()).isEqualByComparingTo("99");
        assertThat(level.bidVolume()).isEqualTo(1_150);
        assertThat(level.bidOrderCount()).isEqualTo(11);
        assertThat(level.ownBidVolume()).isEqualTo(250);
    }

    @Test
    void keepsAllOwnPricesVisibleAndFillsOnlyTheRemainingSlotsFromPublicDepth() {
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of(
                        order(OrderSide.BUY, "1", 100),
                        order(OrderSide.BUY, "97.50", 200),
                        order(OrderSide.BUY, "99", 300)
                ));

        PrivateOrderBookResponse response = service.getOrderBook(1L, "CODE");

        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::bidPrice)
                .containsExactly(new BigDecimal("100"), new BigDecimal("99"),
                        new BigDecimal("98"), new BigDecimal("97.50"), new BigDecimal("1.00"));
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::ownBidVolume)
                .containsExactly(0L, 300L, 0L, 200L, 100L);
    }

    @Test
    void alwaysReturnsFiveRowsEvenWhenBothSidesHaveNoLiquidity() {
        when(bookStateService.loadResidualBidLevels(1L, "EMPTY")).thenReturn(List.of());
        when(bookStateService.loadResidualAskLevels(1L, "EMPTY")).thenReturn(List.of());
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("EMPTY"), anyList()))
                .thenReturn(List.of());

        PrivateOrderBookResponse response = service.getOrderBook(1L, "EMPTY");

        assertThat(response.rows()).hasSize(5);
        assertThat(response.rows()).allSatisfy(row -> {
            assertThat(row.bidPrice()).isNull();
            assertThat(row.askPrice()).isNull();
        });
    }

    @Test
    void buildsAndRanksAFiveLevelBookFromOwnOrdersWhenPublicDepthIsUnavailable() {
        when(bookStateService.loadResidualBidLevels(1L, "EMPTY")).thenReturn(List.of());
        when(bookStateService.loadResidualAskLevels(1L, "EMPTY")).thenReturn(List.of());
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("EMPTY"), anyList()))
                .thenReturn(List.of(
                        order(OrderSide.BUY, "10", 1), order(OrderSide.BUY, "12", 1),
                        order(OrderSide.BUY, "11", 1), order(OrderSide.BUY, "8", 1),
                        order(OrderSide.BUY, "9", 1), order(OrderSide.BUY, "7", 1),
                        order(OrderSide.SELL, "20", 1), order(OrderSide.SELL, "18", 1)
                ));

        PrivateOrderBookResponse response = service.getOrderBook(1L, "EMPTY");

        assertThat(response.rows()).hasSize(5);
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::bidPrice)
                .containsExactly(new BigDecimal("12.00"), new BigDecimal("11.00"),
                        new BigDecimal("10.00"), new BigDecimal("9.00"), new BigDecimal("8.00"));
        assertThat(response.rows()).extracting(PrivateOrderBookLevelResponse::askPrice)
                .containsExactly(new BigDecimal("18.00"), new BigDecimal("20.00"), null, null, null);
    }

    @Test
    void keepsEachUsersOverlayCompletelyIsolatedOnTheSamePublicBook() {
        List<ResidualBookLevel> publicBids = List.of(
                residual("100", 1_000, 10), residual("99", 900, 9),
                residual("98", 800, 8), residual("97", 700, 7), residual("96", 600, 6)
        );
        List<ResidualBookLevel> publicAsks = List.of(
                residual("101", 1_000, 10), residual("102", 900, 9),
                residual("103", 800, 8), residual("104", 700, 7), residual("105", 600, 6)
        );
        when(bookStateService.loadResidualBidLevels(2L, "CODE")).thenReturn(publicBids);
        when(bookStateService.loadResidualAskLevels(2L, "CODE")).thenReturn(publicAsks);
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of(order(OrderSide.BUY, "100.50", 250)));
        when(orderRepository.findActiveOrdersForPrivateBook(
                org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.eq("CODE"), anyList()))
                .thenReturn(List.of());

        PrivateOrderBookResponse firstUsersBook = service.getOrderBook(1L, "CODE");
        PrivateOrderBookResponse secondUsersBook = service.getOrderBook(2L, "CODE");

        assertThat(firstUsersBook.rows().getFirst().bidPrice()).isEqualByComparingTo("100.50");
        assertThat(firstUsersBook.rows().getFirst().ownBidVolume()).isEqualTo(250);
        assertThat(secondUsersBook.rows().getFirst().bidPrice()).isEqualByComparingTo("100");
        assertThat(secondUsersBook.rows()).extracting(PrivateOrderBookLevelResponse::ownBidVolume)
                .containsOnly(0L);
    }

    private ResidualBookLevel residual(String price, long volume, long orderCount) {
        return new ResidualBookLevel(new BigDecimal(price), volume, orderCount, volume);
    }

    private TradingOrder order(OrderSide side, String price, long remainingQuantity) {
        TradingOrder order = new TradingOrder();
        order.setSide(side);
        order.setOrderPrice(new BigDecimal(price));
        order.setRemainingQuantity(remainingQuantity);
        order.setStatus(OrderStatus.REQUESTED);
        return order;
    }
}
