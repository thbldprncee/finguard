package com.finguard.fraud.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule thresholds. Whether a rule is enabled and how much it adds to the risk score
 * live in the fraud_rules table (changeable at runtime via PATCH /api/fraud/rules/{id}).
 */
@Validated
@ConfigurationProperties("finguard.fraud")
public record FraudProperties(
        @Valid @DefaultValue Decision decision,
        @Valid @DefaultValue Rules rules,
        @NotNull @DefaultValue("30s") Duration ruleConfigRefreshInterval
) {

    /**
     * @param onRuleFailure what to do when a rule cannot be evaluated (e.g. Redis is down).
     *                      Treating the rule as "not triggered" would lower the score exactly
     *                      when the system is degraded (fail-open), so the decision is escalated.
     */
    public record Decision(
            @Min(1) @Max(100) @DefaultValue("40") int reviewThreshold,
            @Min(1) @Max(100) @DefaultValue("70") int blockThreshold,
            @NotNull @DefaultValue("REVIEW") FailurePolicy onRuleFailure
    ) {}

    public enum FailurePolicy {
        /** At least REVIEW_REQUIRED: a human looks at it. */
        REVIEW,
        /** BLOCKED: for flows where an unchecked payment is worse than a false decline. */
        BLOCK
    }

    public record Rules(
            @Valid @DefaultValue HighAmount highAmount,
            @Valid @DefaultValue Velocity velocity,
            @Valid @DefaultValue Structuring structuring,
            @Valid @DefaultValue CountryChange countryChange,
            @Valid @DefaultValue NightActivity nightActivity,
            @Valid @DefaultValue SuspiciousMerchant suspiciousMerchant,
            @Valid @DefaultValue NewDevice newDevice,
            @Valid @DefaultValue RepeatedBlocked repeatedBlocked
    ) {}

    /** @param thresholds per-currency limits; 10 000 JPY is not 10 000 USD. */
    public record HighAmount(
            @NotNull @DefaultValue("10000") BigDecimal defaultThreshold,
            Map<String, BigDecimal> thresholds
    ) {
        public HighAmount {
            thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
        }

        public BigDecimal thresholdFor(String currency) {
            return currency == null ? defaultThreshold : thresholds.getOrDefault(currency, defaultThreshold);
        }
    }

    /** Sliding window: counts transactions in the last {@code window} before each transaction. */
    public record Velocity(
            @Min(1) @DefaultValue("5") int maxTransactions,
            @NotNull @DefaultValue("10m") Duration window
    ) {}

    /** Several payments that are each below the high-amount threshold but exceed it together. */
    public record Structuring(
            @NotNull @DefaultValue("24h") Duration window,
            @Min(2) @DefaultValue("3") int minTransactions
    ) {}

    public record CountryChange(@NotNull @DefaultValue("30d") Duration memory) {}

    /** Hours [startHour, endHour) in {@code zone}; startHour > endHour wraps around midnight. */
    public record NightActivity(
            @Min(0) @Max(23) @DefaultValue("0") int startHour,
            @Min(0) @Max(24) @DefaultValue("5") int endHour,
            @NotNull @DefaultValue("UTC") ZoneId zone
    ) {}

    public record SuspiciousMerchant(@DefaultValue({"GAMBLING", "CRYPTO", "ADULT"}) Set<String> categories) {
        public SuspiciousMerchant {
            categories = categories == null ? Set.of()
                    : categories.stream().map(c -> c.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        }
    }

    /** @param treatMissingAsRisky without this, simply omitting the device id bypasses the rule. */
    public record NewDevice(
            @NotNull @DefaultValue("90d") Duration memory,
            @DefaultValue("true") boolean treatMissingAsRisky
    ) {}

    public record RepeatedBlocked(
            @NotNull @DefaultValue("24h") Duration lookback,
            @Min(1) @DefaultValue("1") int minCount
    ) {}
}
