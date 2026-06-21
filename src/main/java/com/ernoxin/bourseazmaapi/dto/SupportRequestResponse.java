package com.ernoxin.bourseazmaapi.dto;

public record SupportRequestResponse(
        Long id,
        String subject,
        String message,
        String status,
        String createdAt
) {
}
