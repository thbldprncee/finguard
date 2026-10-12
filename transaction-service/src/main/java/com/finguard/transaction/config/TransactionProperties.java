package com.finguard.transaction.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/**
 * @param idempotencyKeyTtl how long a client may safely retry a request with the same Idempotency-Key.
 */
@Validated
@ConfigurationProperties("finguard.transactions")
public record TransactionProperties(
        @NotEmpty @DefaultValue({"USD", "EUR", "GBP", "CHF"}) Set<String> supportedCurrencies,
        @NotNull @DefaultValue("1000000") BigDecimal maxAmount,
        @NotNull @DefaultValue("24h") Duration idempotencyKeyTtl,
        @Min(1) @DefaultValue("5") int maxAccountsPerUser
) {}
