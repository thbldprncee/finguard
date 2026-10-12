package com.finguard.fraud.rule;

import com.finguard.fraud.exception.MalformedEventException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/** A validated transaction.created event. Anything unparseable is a {@link MalformedEventException}. */
public record TransactionEvent(
        UUID transactionId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String country,
        String merchantCategory,
        String paymentMethod,
        String deviceId,
        String ipAddress,
        Instant occurredAt
) {

    public static TransactionEvent from(Map<String, Object> event) {
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
