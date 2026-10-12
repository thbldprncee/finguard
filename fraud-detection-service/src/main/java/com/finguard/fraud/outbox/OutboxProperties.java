package com.finguard.fraud.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("finguard.outbox")
public record OutboxProperties(
        @NotNull @DefaultValue("500ms") Duration pollInterval,
        @Min(1) @Max(1000) @DefaultValue("100") int batchSize,
        @NotNull @DefaultValue("10s") Duration sendTimeout,
        @NotNull @DefaultValue("1s") Duration initialBackoff,
        @NotNull @DefaultValue("5m") Duration maxBackoff,
        @NotNull @DefaultValue("7d") Duration retention,
        @NotNull @DefaultValue("1h") Duration cleanupInterval
) {}
