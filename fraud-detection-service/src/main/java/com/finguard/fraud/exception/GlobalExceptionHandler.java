package com.finguard.fraud.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    record Violation(String field, String message) {}

    record ErrorResponse(int status, String error, String message, String path, Instant timestamp,
                         List<Violation> violations) {
        static ErrorResponse of(HttpStatus status, String message, HttpServletRequest request) {
            return of(status, message, request, null);
        }

        static ErrorResponse of(HttpStatus status, String message, HttpServletRequest request, List<Violation> violations) {
            return new ErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI(),
                    Instant.now(), violations);
        }
    }

    /** @Valid @RequestBody failed: one entry per invalid field, plus class-level checks. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Violation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(e -> new Violation(e instanceof FieldError f ? f.getField() : e.getObjectName(), e.getDefaultMessage()))
                .toList();
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Request validation failed", request, violations);
    }

    /** Constraint on a header, path variable or request parameter (Spring MVC method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<Violation> violations = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new Violation(parameterName(r), messageOf(e))))
                .toList();
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Request validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Violation> violations = ex.getConstraintViolations().stream()
                .map(v -> new Violation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Request validation failed", request, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, PropertyReferenceException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformed(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Malformed request", request);
    }

    @ExceptionHandler(InvalidHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidHeader(InvalidHeaderException ex, HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
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

    /** If-Match carried an outdated version: the change was made on a stale copy. */
    @ExceptionHandler(StaleVersionException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public ErrorResponse handleStaleVersion(StaleVersionException ex, HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.PRECONDITION_FAILED, ex.getMessage(), request);
    }

    /** Two updates with the same version raced; @Version let only the first one through. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public ErrorResponse handleOptimisticLock(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.PRECONDITION_FAILED,
                "The resource was modified by someone else; reload it and try again", request);
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_REQUIRED)
    public ErrorResponse handlePreconditionRequired(PreconditionRequiredException ex, HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.PRECONDITION_REQUIRED, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    /** Header/param name as declared in the mapping annotation, falling back to the Java parameter name. */
    private static String parameterName(ParameterValidationResult result) {
        var p = result.getMethodParameter();
        var header = p.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) return header.value();
        var param = p.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.value().isEmpty()) return param.value();
        return p.getParameterName() != null ? p.getParameterName() : "parameter";
    }

    private static String messageOf(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid value";
    }
}
