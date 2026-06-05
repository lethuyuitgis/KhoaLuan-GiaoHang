package com.shop.delivery.shared.api;

import com.shop.delivery.shared.exception.AuthenticationException;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.DomainException;
import com.shop.delivery.shared.exception.ExternalServiceException;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Map domain & framework exceptions to HTTP status + ApiError body.
 * Spec section 11.3 — 3-layer error handling.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e) {
        return status(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException e) {
        return status(HttpStatus.UNAUTHORIZED, e);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ValidationException e) {
        return status(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException e) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException e) {
        return status(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiError> handleExternalService(ExternalServiceException e) {
        return status(HttpStatus.BAD_GATEWAY, e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleArgValidation(MethodArgumentNotValidException e) {
        String traceId = newTraceId();
        List<ApiError.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .toList();
        log.warn("Validation failed [{}]: {}", traceId, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("VALIDATION_FAILED", "Dữ liệu không hợp lệ", traceId, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException e) {
        String traceId = newTraceId();
        log.warn("Malformed request body [{}]: {}", traceId, e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("MALFORMED_REQUEST", "Yêu cầu không hợp lệ (JSON sai định dạng)", traceId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException e) {
        String traceId = newTraceId();
        log.warn("ResponseStatusException [{}] status={} reason={}", traceId, e.getStatusCode(), e.getReason());
        return ResponseEntity.status(e.getStatusCode())
            .body(new ApiError(e.getReason() != null ? e.getReason() : "ERROR",
                               e.getReason() != null ? e.getReason() : e.getMessage(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception e) {
        String traceId = newTraceId();
        log.error("Unhandled exception [{}]", traceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError("INTERNAL_ERROR", "Có lỗi xảy ra, vui lòng thử lại sau", traceId));
    }

    private ResponseEntity<ApiError> status(HttpStatus status, DomainException e) {
        String traceId = newTraceId();
        log.warn("DomainException [{}] code={} msg={}", traceId, e.getCode(), e.getMessage());
        return ResponseEntity.status(status)
            .body(new ApiError(e.getCode(), e.getMessage(), traceId));
    }

    private ApiError.FieldError toFieldError(FieldError fe) {
        return new ApiError.FieldError(fe.getField(),
            fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
