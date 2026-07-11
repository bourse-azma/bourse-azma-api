package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.BookSide;
import com.ernoxin.bourseazmaapi.model.UserLiquidityConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserLiquidityConsumptionRepository extends JpaRepository<UserLiquidityConsumption, Long> {

    List<UserLiquidityConsumption> findAllByUserIdAndInstrumentCode(Long userId, String instrumentCode);

    Optional<UserLiquidityConsumption> findByUserIdAndInstrumentCodeAndBookSideAndPrice(
            Long userId, String instrumentCode, BookSide bookSide, BigDecimal price);
}
