package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradingOrderRepository extends JpaRepository<TradingOrder, Long> {
    List<TradingOrder> findAllByUserIdOrderByOrderTimeDesc(Long userId);

    Optional<TradingOrder> findByIdAndUserId(Long id, Long userId);

    List<TradingOrder> findAllByStatusOrderByOrderTimeAsc(OrderStatus status);

    List<TradingOrder> findAllByUserIdAndInstrumentCodeAndSideAndStatus(Long userId,
                                                                        String instrumentCode,
                                                                        OrderSide side,
                                                                        OrderStatus status);

    List<TradingOrder> findAllByUserIdAndSideAndStatus(Long userId, OrderSide side, OrderStatus status);

    long countByUserId(Long userId);

    boolean existsByUserIdAndInstrumentCodeAndOrderPriceAndQuantity(Long userId,
                                                                    String instrumentCode,
                                                                    java.math.BigDecimal orderPrice,
                                                                    Long quantity);

    @Query("SELECT o FROM TradingOrder o WHERE o.instrumentCode = :instrumentCode " +
            "AND o.side = :side AND o.status IN :statuses " +
            "ORDER BY o.orderPrice ASC, o.orderTime ASC")
    List<TradingOrder> findActiveSellOrders(@Param("instrumentCode") String instrumentCode,
                                            @Param("side") OrderSide side,
                                            @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM TradingOrder o WHERE o.instrumentCode = :instrumentCode " +
            "AND o.side = :side AND o.status IN :statuses " +
            "ORDER BY o.orderPrice DESC, o.orderTime ASC")
    List<TradingOrder> findActiveBuyOrders(@Param("instrumentCode") String instrumentCode,
                                           @Param("side") OrderSide side,
                                           @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.remainingQuantity), 0) FROM TradingOrder o " +
            "WHERE o.user.id = :userId AND o.instrumentCode = :instrumentCode " +
            "AND o.side = 'SELL' AND o.status IN ('REQUESTED', 'PARTIALLY_FILLED', 'TRIGGER_PENDING')")
    long sumReservedSellQuantity(@Param("userId") Long userId,
                                 @Param("instrumentCode") String instrumentCode);

    @Query("SELECT COALESCE(SUM(o.orderPrice * o.remainingQuantity), 0) FROM TradingOrder o " +
            "WHERE o.user.id = :userId AND o.side = 'BUY' " +
            "AND o.status IN ('REQUESTED', 'PARTIALLY_FILLED', 'TRIGGER_PENDING')")
    java.math.BigDecimal sumReservedBuyValue(@Param("userId") Long userId);

    @Query("SELECT DISTINCT o.instrumentCode FROM TradingOrder o " +
            "WHERE o.status IN ('REQUESTED', 'PARTIALLY_FILLED')")
    List<String> findDistinctInstrumentCodesWithActiveOrders();
}
