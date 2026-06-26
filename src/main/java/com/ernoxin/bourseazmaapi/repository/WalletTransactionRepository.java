package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    Page<WalletTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
