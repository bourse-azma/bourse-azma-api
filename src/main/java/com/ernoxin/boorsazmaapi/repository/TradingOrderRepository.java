package com.ernoxin.boorsazmaapi.repository;

import com.ernoxin.boorsazmaapi.model.TradingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradingOrderRepository extends JpaRepository<TradingOrder, Long> {
    List<TradingOrder> findAllByUserIdOrderByOrderTimeDesc(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndInstrumentCodeAndOrderPriceAndQuantity(Long userId,
                                                                    String instrumentCode,
                                                                    java.math.BigDecimal orderPrice,
                                                                    Long quantity);
}
