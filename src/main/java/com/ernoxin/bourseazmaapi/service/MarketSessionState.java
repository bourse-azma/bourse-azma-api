package com.ernoxin.bourseazmaapi.service;

/**
 * A missing TSETMC response must never be interpreted as a closed market.
 */
public enum MarketSessionState {
    OPEN,
    CLOSED,
    UNKNOWN
}
