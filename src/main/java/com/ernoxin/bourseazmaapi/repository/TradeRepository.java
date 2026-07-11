package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    @Query("SELECT t FROM Trade t WHERE t.buyer.id = :userId OR t.seller.id = :userId ORDER BY t.executedAt DESC")
    Page<Trade> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.buyer.id = :userId OR t.seller.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
