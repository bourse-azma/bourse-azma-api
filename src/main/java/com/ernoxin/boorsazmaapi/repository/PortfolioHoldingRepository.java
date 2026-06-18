package com.ernoxin.boorsazmaapi.repository;

import com.ernoxin.boorsazmaapi.model.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    List<PortfolioHolding> findAllByUserIdOrderByAcquiredAtDesc(Long userId);

    List<PortfolioHolding> findAllByUserIdAndInstrumentCode(Long userId, String instrumentCode);

    long countByUserId(Long userId);

    boolean existsByUserIdAndInstrumentCodeAndBuyPriceAndQuantity(Long userId,
                                                                  String instrumentCode,
                                                                  java.math.BigDecimal buyPrice,
                                                                  Long quantity);
}
