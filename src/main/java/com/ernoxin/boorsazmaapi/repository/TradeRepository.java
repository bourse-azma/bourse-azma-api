package com.ernoxin.boorsazmaapi.repository;

import com.ernoxin.boorsazmaapi.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findAllByBuyOrderIdOrSellOrderIdOrderByExecutedAtDesc(Long buyOrderId, Long sellOrderId);
}
