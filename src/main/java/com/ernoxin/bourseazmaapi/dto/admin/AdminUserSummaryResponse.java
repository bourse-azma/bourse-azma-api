package com.ernoxin.bourseazmaapi.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminUserSummaryResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String nationalCode,
        String phoneNumber,
        String email,
        BigDecimal balance,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lastSeenAt,
        String lastLoginIp,
        boolean blocked,
        Instant blockedAt,
        String blockedReason,
        boolean online,
        long orderCount,
        long tradeCount,
        long holdingCount,
        long ticketCount
) {
}
