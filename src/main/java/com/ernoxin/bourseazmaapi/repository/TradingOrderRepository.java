package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradingOrderRepository extends JpaRepository<TradingOrder, Long> {
    Page<TradingOrder> findAllByUserIdOrderByOrderTimeDesc(Long userId, Pageable pageable);

    Page<TradingOrder> findAllByUserIdAndStatusInOrderByOrderTimeDesc(
            Long userId, List<OrderStatus> statuses, Pageable pageable);

    List<TradingOrder> findAllByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    Optional<TradingOrder> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM TradingOrder o WHERE o.id = :id")
    Optional<TradingOrder> findByIdForUpdate(@Param("id") Long id);

    List<TradingOrder> findAllByStatusOrderByOrderTimeAsc(OrderStatus status);

    @Query("SELECT o FROM TradingOrder o WHERE o.user.id = :userId " +
            "AND o.instrumentCode = :instrumentCode AND o.status IN :statuses " +
            "AND o.remainingQuantity > 0 ORDER BY o.orderTime ASC")
    List<TradingOrder> findActiveOrdersForPrivateBook(@Param("userId") Long userId,
                                                      @Param("instrumentCode") String instrumentCode,
                                                      @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o.id FROM TradingOrder o WHERE o.user.id = :userId " +
            "AND o.instrumentCode = :instrumentCode AND o.status IN :statuses " +
            "AND o.remainingQuantity > 0 AND o.side = com.ernoxin.bourseazmaapi.model.OrderSide.BUY " +
            "ORDER BY o.orderPrice DESC, o.orderTime ASC")
    List<Long> findActiveBuyOrderIdsForMatching(@Param("userId") Long userId,
                                                @Param("instrumentCode") String instrumentCode,
                                                @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o.id FROM TradingOrder o WHERE o.user.id = :userId " +
            "AND o.instrumentCode = :instrumentCode AND o.status IN :statuses " +
            "AND o.remainingQuantity > 0 AND o.side = com.ernoxin.bourseazmaapi.model.OrderSide.SELL " +
            "ORDER BY o.orderPrice ASC, o.orderTime ASC")
    List<Long> findActiveSellOrderIdsForMatching(@Param("userId") Long userId,
                                                 @Param("instrumentCode") String instrumentCode,
                                                 @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM TradingOrder o WHERE o.user.id = :userId " +
            "AND o.instrumentCode = :instrumentCode AND o.status IN :statuses " +
            "AND o.remainingQuantity > 0 AND o.side = com.ernoxin.bourseazmaapi.model.OrderSide.SELL " +
            "AND o.orderPrice <= :maxPrice AND o.id <> :excludeOrderId " +
            "ORDER BY o.orderPrice ASC, o.orderTime ASC")
    List<TradingOrder> findOwnRestingSellsForMatch(@Param("userId") Long userId,
                                                   @Param("instrumentCode") String instrumentCode,
                                                   @Param("maxPrice") java.math.BigDecimal maxPrice,
                                                   @Param("statuses") List<OrderStatus> statuses,
                                                   @Param("excludeOrderId") Long excludeOrderId);

    @Query("SELECT o FROM TradingOrder o WHERE o.user.id = :userId " +
            "AND o.instrumentCode = :instrumentCode AND o.status IN :statuses " +
            "AND o.remainingQuantity > 0 AND o.side = com.ernoxin.bourseazmaapi.model.OrderSide.BUY " +
            "AND o.orderPrice >= :minPrice AND o.id <> :excludeOrderId " +
            "ORDER BY o.orderPrice DESC, o.orderTime ASC")
    List<TradingOrder> findOwnRestingBuysForMatch(@Param("userId") Long userId,
                                                  @Param("instrumentCode") String instrumentCode,
                                                  @Param("minPrice") java.math.BigDecimal minPrice,
                                                  @Param("statuses") List<OrderStatus> statuses,
                                                  @Param("excludeOrderId") Long excludeOrderId);

    @Query("SELECT o FROM TradingOrder o WHERE o.user.id = :userId " +
            "AND o.instrumentCode = :instrumentCode AND o.status IN :statuses " +
            "AND o.remainingQuantity > 0 AND o.side = com.ernoxin.bourseazmaapi.model.OrderSide.BUY " +
            "ORDER BY o.orderPrice DESC, o.orderTime ASC")
    List<TradingOrder> findActiveBuysPriceTime(@Param("userId") Long userId,
                                               @Param("instrumentCode") String instrumentCode,
                                               @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM TradingOrder o WHERE o.user.id = :userId " +
            "AND o.instrumentCode = :instrumentCode AND o.status IN :statuses " +
            "AND o.remainingQuantity > 0 AND o.side = com.ernoxin.bourseazmaapi.model.OrderSide.SELL " +
            "ORDER BY o.orderPrice ASC, o.orderTime ASC")
    List<TradingOrder> findActiveSellsPriceTime(@Param("userId") Long userId,
                                                @Param("instrumentCode") String instrumentCode,
                                                @Param("statuses") List<OrderStatus> statuses);

    long countByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(o.remainingQuantity), 0) FROM TradingOrder o " +
            "WHERE o.user.id = :userId AND o.instrumentCode = :instrumentCode " +
            "AND o.side = 'SELL' AND o.status IN ('REQUESTED', 'PARTIALLY_FILLED', 'TRIGGER_PENDING')")
    long sumReservedSellQuantity(@Param("userId") Long userId,
                                 @Param("instrumentCode") String instrumentCode);

    @Query("SELECT COALESCE(SUM(o.orderPrice * o.remainingQuantity), 0) FROM TradingOrder o " +
            "WHERE o.user.id = :userId AND o.side = 'BUY' " +
            "AND o.status IN ('REQUESTED', 'PARTIALLY_FILLED', 'TRIGGER_PENDING')")
    java.math.BigDecimal sumReservedBuyValue(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(o.orderPrice * o.remainingQuantity), 0) FROM TradingOrder o " +
            "WHERE o.user.id = :userId AND o.side = 'BUY' " +
            "AND o.status IN ('REQUESTED', 'PARTIALLY_FILLED', 'TRIGGER_PENDING') " +
            "AND o.id <> :excludeOrderId")
    java.math.BigDecimal sumReservedBuyValueExcluding(@Param("userId") Long userId,
                                                      @Param("excludeOrderId") Long excludeOrderId);

    @Query("SELECT COALESCE(SUM(o.remainingQuantity), 0) FROM TradingOrder o " +
            "WHERE o.user.id = :userId AND o.instrumentCode = :instrumentCode " +
            "AND o.side = 'SELL' AND o.status IN ('REQUESTED', 'PARTIALLY_FILLED', 'TRIGGER_PENDING') " +
            "AND o.id <> :excludeOrderId")
    long sumReservedSellQuantityExcluding(@Param("userId") Long userId,
                                          @Param("instrumentCode") String instrumentCode,
                                          @Param("excludeOrderId") Long excludeOrderId);

    @Query("SELECT DISTINCT o.instrumentCode FROM TradingOrder o " +
            "WHERE o.status IN ('REQUESTED', 'PARTIALLY_FILLED') AND o.remainingQuantity > 0")
    List<String> findDistinctInstrumentCodesWithActiveOrders();

    @Query("SELECT DISTINCT o.user.id FROM TradingOrder o " +
            "WHERE o.instrumentCode = :instrumentCode " +
            "AND o.status IN ('REQUESTED', 'PARTIALLY_FILLED') AND o.remainingQuantity > 0")
    List<Long> findDistinctUserIdsWithActiveOrders(@Param("instrumentCode") String instrumentCode);
}
