package com.ernoxin.bourseazmaapi.dto.admin;

import java.time.Instant;

public record AdminActivityResponse(
        Long id,
        String activityType,
        String ipAddress,
        Instant occurredAt
) {
}
