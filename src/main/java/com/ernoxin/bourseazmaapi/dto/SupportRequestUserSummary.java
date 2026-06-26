package com.ernoxin.bourseazmaapi.dto;

public record SupportRequestUserSummary(
        Long id,
        String displayName,
        String username,
        String firstName,
        String lastName,
        String nationalCode,
        String phoneNumber,
        String email
) {
}
