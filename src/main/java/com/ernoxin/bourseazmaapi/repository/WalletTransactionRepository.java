package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    @EntityGraph(attributePaths = "performedByAdmin")
    Page<WalletTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT
                COUNT(w) AS totalCount,
                COALESCE(SUM(w.amount), 0) AS totalNet,
                COALESCE(SUM(CASE WHEN w.amount > 0 THEN 1 ELSE 0 END), 0) AS inflowCount,
                COALESCE(SUM(CASE WHEN w.amount < 0 THEN 1 ELSE 0 END), 0) AS outflowCount
            FROM WalletTransaction w
            WHERE w.user.id = :userId
            """)
    WalletTransactionSummary summarizeByUserId(@Param("userId") Long userId);
}
