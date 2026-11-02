package com.finguard.fraud.rule;

import com.finguard.fraud.exception.MalformedEventException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A validated transaction.created event. Anything unparseable or out of range is a
 * {@link MalformedEventException}: it can never succeed, so it skips the retries and goes to the DLT.
 * The limits mirror the transaction-service API and database, so a valid event is never rejected.
 */
public record TransactionEvent(
        @NotNull UUID transactionId,
        @NotNull UUID userId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @Pattern(regexp = "^[A-Z]{2,3}$") String country,
        @Size(max = 50) String merchantCategory,
        @Size(max = 30) String paymentMethod,
        @Size(max = 100) String deviceId,
        @Size(max = 45) String ipAddress,
        @NotNull Instant occurredAt
) {

    /**
     * Thread-safe and built once; the factory stays open for the lifetime of the application.
     * Spring's Validator bean is not reachable from this static factory method.
     */
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    public static TransactionEvent from(Map<String, Object> event) {
        TransactionEvent parsed = parse(event);
        Set<ConstraintViolation<TransactionEvent>> violations = VALIDATOR.validate(parsed);
        if (!violations.isEmpty()) {
            throw new MalformedEventException("Invalid transaction.created event: " + violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; ")));
        }
        return parsed;
    }

    private static TransactionEvent parse(Map<String, Object> event) {
        try {
            Object occurredAt = event.get("occurredAt");
            return new TransactionEvent(
                    UUID.fromString(required(event, "transactionId")),
                    UUID.fromString(required(event, "userId")),
                    new BigDecimal(required(event, "amount")),
                    required(event, "currency"),
                    required(event, "country"),
                    optional(event, "merchantCategory"),
                    optional(event, "paymentMethod"),
                    optional(event, "deviceId"),
                    optional(event, "ipAddress"),
                    occurredAt != null ? Instant.parse(occurredAt.toString()) : Instant.now());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new MalformedEventException("Invalid transaction.created event: " + e.getMessage());
        }
    }

    public RuleContext toRuleContext() {
        return RuleContext.builder()
                .transactionId(transactionId).userId(userId).amount(amount)
                .currency(currency).country(country).merchantCategory(merchantCategory)
                .paymentMethod(paymentMethod).deviceId(deviceId).ipAddress(ipAddress)
                .occurredAt(occurredAt)
                .build();
    }

    private static String required(Map<String, Object> event, String field) {
        Object value = event.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new MalformedEventException("Missing field " + field);
        }
        return value.toString();
    }

    private static String optional(Map<String, Object> event, String field) {
        Object value = event.get(field);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
