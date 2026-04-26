package com.finguard.transaction.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCreatedEvent(
        UUID transactionId,
        String externalTransactionId,
        UUID userId,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        String currency,
        String country,
        String merchantCategory,
        String paymentMethod,
        String ipAddress,
        String deviceId,
        String correlationId,
        Instant occurredAt
) {}
