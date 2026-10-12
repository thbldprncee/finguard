package com.finguard.fraud.rule;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class RuleContext {
    private UUID transactionId;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private String country;
    private String merchantCategory;
    private String paymentMethod;
    private String deviceId;
    private String ipAddress;
    /** When the transaction was created, not when it is analysed (Kafka lag, retries). */
    private Instant occurredAt;
}
