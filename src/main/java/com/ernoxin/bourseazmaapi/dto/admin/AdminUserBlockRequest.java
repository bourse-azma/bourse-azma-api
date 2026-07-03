package com.ernoxin.bourseazmaapi.dto.admin;

import jakarta.validation.constraints.Size;

public record AdminUserBlockRequest(
        boolean blocked,
        @Size(max = 500, message = "دلیل مسدودی حداکثر ۵۰۰ کاراکتر است.") String reason
) {
}
