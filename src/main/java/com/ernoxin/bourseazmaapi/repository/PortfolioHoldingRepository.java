package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.PortfolioHolding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    List<PortfolioHolding> findAllByUserIdOrderByAcquiredAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM PortfolioHolding h WHERE h.user.id = :userId " +
            "AND h.instrumentCode = :instrumentCode ORDER BY h.acquiredAt ASC, h.id ASC")
    List<PortfolioHolding> findAllByUserIdAndInstrumentCodeForUpdate(
            @Param("userId") Long userId,
            @Param("instrumentCode") String instrumentCode);

    long countByUserId(Long userId);

    boolean existsByUserIdAndInstrumentCodeAndBuyPriceAndQuantity(Long userId,
                                                                  String instrumentCode,
                                                                  java.math.BigDecimal buyPrice,
                                                                  Long quantity);
}
