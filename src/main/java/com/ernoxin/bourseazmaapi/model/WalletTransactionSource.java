package com.ernoxin.bourseazmaapi.model;

/**
 * Business reason for a wallet ledger entry. Persisted as a string for legacy compatibility.
 */
public enum WalletTransactionSource {
    TRADE_BUY,
    TRADE_SELL,
    DEPOSIT,
    WITHDRAWAL,
    INITIAL_BALANCE,
    ADMIN_ADJUSTMENT
}
