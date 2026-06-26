package com.ernoxin.bourseazmaapi.dto;

public record SupportRequestResponse(
        Long id,
        String subject,
        String message,
        String status,
        String category,
        String priority,
        Integer rating,
        String ratingComment,
        int messageCount,
        String lastReplyAt,
        String createdAt,
        String updatedAt,
        String closedAt,
        String closedBy,
        SupportRequestUserSummary user
) {
}
