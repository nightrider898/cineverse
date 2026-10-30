package com.cineverse.exception;

import com.cineverse.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        if (ex.getCode().status().is5xxServerError()) {
            log.error("API error {}: {}", ex.getCode(), ex.getMessage());
        } else {
            log.debug("API error {}: {}", ex.getCode(), ex.getMessage());
        }
        return build(ex.getCode().status(), ex.getCode().name(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(), "Please check the highlighted fields", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fields.put(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(), "Request validation failed", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        String message = ex instanceof MissingRequestHeaderException mh ? "Missing header " + mh.getHeaderName()
                : ex instanceof MissingServletRequestParameterException mp ? "Missing parameter " + mp.getParameterName()
                : "Malformed request";
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(), message, null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_DISABLED.name(), ErrorCode.ACCOUNT_DISABLED.defaultMessage(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.defaultMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.defaultMessage(), null);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleLocking(Exception ex) {
        log.info("Concurrent modification rejected: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(), "The resource was modified concurrently, please retry", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("ex_shows_no_overlap")) {
            return build(HttpStatus.CONFLICT, ErrorCode.SHOW_OVERLAP.name(), ErrorCode.SHOW_OVERLAP.defaultMessage(), null);
        }
        log.warn("Data integrity violation: {}", msg);
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(), "Request conflicts with existing data", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.name(), "Endpoint not found", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message, fields));
    }
}
