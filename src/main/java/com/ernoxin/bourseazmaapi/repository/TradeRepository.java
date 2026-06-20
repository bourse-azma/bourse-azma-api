package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findAllByBuyOrderIdOrSellOrderIdOrderByExecutedAtDesc(Long buyOrderId, Long sellOrderId);
}
