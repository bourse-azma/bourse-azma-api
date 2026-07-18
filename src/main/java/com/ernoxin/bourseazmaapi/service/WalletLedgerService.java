package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import com.ernoxin.bourseazmaapi.model.WalletTransactionSource;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WalletLedgerService {

    private final WalletTransactionRepository walletTransactionRepository;

    public void recordBalanceChange(User user, BigDecimal amount, String description) {
        recordBalanceChange(user, amount, description, null);
    }

    public void recordBalanceChange(User user, BigDecimal amount, String description,
                                    WalletTransactionSource source) {
        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setBalanceAfter(user.getBalance());
        tx.setDescription(description);
        tx.setSource(source == null ? null : source.name());
        tx.setCreatedAt(Instant.now());
        walletTransactionRepository.save(tx);
    }
}
