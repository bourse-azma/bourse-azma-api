package com.ernoxin.boorsazmaapi.dto.api;

import java.util.Map;

public record ErrorResult(
        String category,
        String detail,
        Map<String, String> errors
) {
    public static ErrorResult validation(Map<String, String> errors) {
        return new ErrorResult("اعتبارسنجی", null, errors);
    }

    public static ErrorResult conflict(String detail) {
        return new ErrorResult("تداخل", detail, null);
    }

    public static ErrorResult notFound(String detail) {
        return new ErrorResult("عدم یافتن", detail, null);
    }

    public static ErrorResult unauthorized(String detail) {
        return new ErrorResult("احراز هویت", detail, null);
    }

    public static ErrorResult forbidden(String detail) {
        return new ErrorResult("دسترسی", detail, null);
    }

    public static ErrorResult tooManyRequests(String detail) {
        return new ErrorResult("محدودیت درخواست", detail, null);
    }

    public static ErrorResult internal(String detail) {
        return new ErrorResult("داخلی", detail, null);
    }
}
