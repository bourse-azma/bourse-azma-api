package com.ernoxin.bourseazmaapi.repository;

import java.math.BigDecimal;

public interface WalletTransactionSummary {
    long getTotalCount();

    BigDecimal getTotalNet();

    long getInflowCount();

    long getOutflowCount();
}
