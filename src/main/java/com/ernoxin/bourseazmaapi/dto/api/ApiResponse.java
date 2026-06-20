package com.ernoxin.bourseazmaapi.dto.api;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

import java.time.Instant;

public record ApiResponse<T>(
        String referenceId,
        String timestamp,
        int code,
        String message,
        T result
) {
    private static final String SUCCESS_MESSAGE = "عملیات با موفقیت انجام شد";

    public static <T> ApiResponse<T> ok(T result) {
        return of(HttpStatus.OK, SUCCESS_MESSAGE, result);
    }

    public static <T> ApiResponse<T> created(T result) {
        return of(HttpStatus.CREATED, SUCCESS_MESSAGE, result);
    }

    public static <T> ApiResponse<T> of(HttpStatus status, String message, @Nullable T result) {
        return new ApiResponse<>(
                MDC.get("referenceId"),
                Instant.now().toString(),
                status.value(),
                message,
                result
        );
    }
}
