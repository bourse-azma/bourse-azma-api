package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
