package com.ernoxin.bourseazmaapi.exception;

import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.dto.api.ErrorResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String VALIDATION_MESSAGE = "خطای اعتبارسنجی";
    private static final String CONFLICT_MESSAGE = "تداخل اطلاعات";
    private static final String NOT_FOUND_MESSAGE = "یافت نشد";
    private static final String ACCESS_DENIED_MESSAGE = "عدم دسترسی";
    private static final String AUTH_REQUIRED_MESSAGE = "احراز هویت لازم است";
    private static final String INVALID_CREDENTIALS_MESSAGE = "نام کاربری یا رمز عبور اشتباه است";
    private static final String INTERNAL_MESSAGE = "خطای سرور";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), defaultIfBlank(fieldError.getDefaultMessage()));
        }

        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_MESSAGE,
                ErrorResult.validation(fieldErrors)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleBind(BindException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), defaultIfBlank(fieldError.getDefaultMessage()));
        }

        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_MESSAGE,
                ErrorResult.validation(fieldErrors)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String fieldName = ex.getName() == null ? "پارامتر" : ex.getName();
        Map<String, String> errors = Map.of(fieldName, "مقدار پارامتر معتبر نیست.");
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_MESSAGE,
                ErrorResult.validation(errors)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Map<String, String> errors = Map.of("body", "ساختار درخواست نامعتبر است.");
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_MESSAGE,
                ErrorResult.validation(errors)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String field = violation.getPropertyPath() == null
                    ? "پارامتر"
                    : violation.getPropertyPath().toString();
            errors.putIfAbsent(field, defaultIfBlank(violation.getMessage()));
        }
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_MESSAGE,
                ErrorResult.validation(errors)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleDuplicate(DuplicateResourceException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.CONFLICT,
                CONFLICT_MESSAGE,
                ErrorResult.conflict(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleNotFound(ResourceNotFoundException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.NOT_FOUND,
                NOT_FOUND_MESSAGE,
                ErrorResult.notFound(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleNoResourceFound(NoResourceFoundException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.NOT_FOUND,
                NOT_FOUND_MESSAGE,
                ErrorResult.notFound("مسیر مورد نظر یافت نشد.")
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                        HttpServletRequest request) {
        log.error("Data integrity violation. path={} method={}", request.getRequestURI(), request.getMethod(), ex);
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.CONFLICT,
                CONFLICT_MESSAGE,
                ErrorResult.conflict("اطلاعات تکراری یا متناقض ثبت شده است.")
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleIllegalArgument(IllegalArgumentException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_MESSAGE,
                ErrorResult.validation(Map.of("value", defaultIfBlank(ex.getMessage())))
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleInvalidCredentials(InvalidCredentialsException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.UNAUTHORIZED,
                INVALID_CREDENTIALS_MESSAGE,
                ErrorResult.unauthorized(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccountBlockedException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleAccountBlocked(AccountBlockedException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.FORBIDDEN,
                "حساب کاربری مسدود است",
                ErrorResult.forbidden(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleInvalidCurrentPassword(InvalidCurrentPasswordException ex) {
        Map<String, String> errors = Map.of("currentPassword", defaultIfBlank(ex.getMessage()));
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.BAD_REQUEST,
                VALIDATION_MESSAGE,
                ErrorResult.validation(errors)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(LoginBruteForceBlockedException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleLoginBruteForceBlocked(LoginBruteForceBlockedException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.TOO_MANY_REQUESTS,
                "تلاش‌های ورود بیش از حد مجاز",
                ErrorResult.tooManyRequests(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleAccessDenied(AccessDeniedException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.FORBIDDEN,
                ACCESS_DENIED_MESSAGE,
                ErrorResult.forbidden("شما به این عملیات دسترسی ندارید.")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleAuthenticationRequired(InsufficientAuthenticationException ex) {
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.UNAUTHORIZED,
                AUTH_REQUIRED_MESSAGE,
                ErrorResult.unauthorized("برای انجام این عملیات باید وارد شوید.")
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResult>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error. path={} method={}", request.getRequestURI(), request.getMethod(), ex);
        ApiResponse<ErrorResult> body = ApiResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_MESSAGE,
                ErrorResult.internal("خطای داخلی سیستم رخ داده است. لطفا مجددا تلاش کنید.")
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String defaultIfBlank(String text) {
        if (text == null || text.isBlank()) {
            return "مقدار واردشده معتبر نیست.";
        }
        return text;
    }
}
