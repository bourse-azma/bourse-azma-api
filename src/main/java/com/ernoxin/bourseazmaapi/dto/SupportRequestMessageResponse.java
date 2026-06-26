package com.ernoxin.bourseazmaapi.dto;

public record SupportRequestMessageResponse(
        Long id,
        String message,
        String authorRole,
        String authorName,
        Long authorUserId,
        String createdAt,
        String editedAt,
        String seenAt
) {
}
