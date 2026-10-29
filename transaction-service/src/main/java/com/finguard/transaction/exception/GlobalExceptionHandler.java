package com.finguard.transaction.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    record FieldError(String field, String message) {}

    record ErrorResponse(int status, String error, String message, String path, Instant timestamp, List<FieldError> fieldErrors) {
        static ErrorResponse of(HttpStatus status, String message, HttpServletRequest request) {
            return new ErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI(), Instant.now(), null);
        }
    }

    /** @Valid @RequestBody failed: one entry per invalid field (or class-level check). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> new FieldError(
                        e instanceof org.springframework.validation.FieldError f ? f.getField() : e.getObjectName(),
                        e.getDefaultMessage()))
                .toList();
        return validationFailed(errors, request);
    }

    /** A constraint on a header, path variable or request parameter (Spring MVC method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        var errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldError(parameterName(r), e.getDefaultMessage())))
                .toList();
        return validationFailed(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validationFailed(errors, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformed(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Malformed request", request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadSort(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Invalid sort property", request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler({BusinessRuleException.class, IdempotencyKeyReuseException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleBusinessRule(RuntimeException ex, HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.CONFLICT, "Concurrent modification, please retry", request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private static ErrorResponse validationFailed(List<FieldError> errors, HttpServletRequest request) {
        return new ErrorResponse(400, "Validation Failed", "Request validation failed", request.getRequestURI(), Instant.now(), errors);
    }

    /** Header/param name as declared in the mapping annotation, falling back to the Java parameter name. */
    private static String parameterName(ParameterValidationResult result) {
        var p = result.getMethodParameter();
        var header = p.getParameterAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);
        if (header != null && !header.value().isEmpty()) return header.value();
        var param = p.getParameterAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
        if (param != null && !param.value().isEmpty()) return param.value();
        return p.getParameterName() != null ? p.getParameterName() : "parameter";
    }
}
