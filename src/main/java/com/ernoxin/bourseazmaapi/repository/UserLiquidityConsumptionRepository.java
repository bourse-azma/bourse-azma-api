package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.BookSide;
import com.ernoxin.bourseazmaapi.model.UserLiquidityConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserLiquidityConsumptionRepository extends JpaRepository<UserLiquidityConsumption, Long> {

    List<UserLiquidityConsumption> findAllByUserIdAndInstrumentCode(Long userId, String instrumentCode);

    Optional<UserLiquidityConsumption> findByUserIdAndInstrumentCodeAndBookSideAndPrice(
            Long userId, String instrumentCode, BookSide bookSide, BigDecimal price);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserLiquidityConsumption consumption " +
            "WHERE consumption.updatedAt < :cutoff")
    int deleteAllUpdatedBefore(@Param("cutoff") Instant cutoff);
}
