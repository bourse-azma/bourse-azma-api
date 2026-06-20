package com.ernoxin.bourseazmaapi.service;

import java.math.BigDecimal;

public record OrderBookPriceRange(BigDecimal min, BigDecimal max) {
}
