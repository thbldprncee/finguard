package com.finguard.fraud.rule;

import com.finguard.fraud.rule.impl.HighAmountRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HighAmountRuleTest {

    private final HighAmountRule rule = new HighAmountRule(TestFraudProperties.with(Map.of(
            "finguard.fraud.rules.high-amount.default-threshold", "10000",
            "finguard.fraud.rules.high-amount.thresholds.JPY", "1500000")));

    private RuleContext ctx(String amount, String currency) {
        return RuleContext.builder()
                .transactionId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal(amount)).currency(currency).country("US")
                .paymentMethod("CARD").occurredAt(Instant.now())
                .build();
    }

    @Test
    void doesNotTrigger_whenAmountBelowThreshold() {
        assertThat(rule.evaluate(ctx("5000", "USD")).triggered()).isFalse();
    }

    @Test
    void triggers_whenAmountExceedsThreshold() {
        RuleResult result = rule.evaluate(ctx("15000", "USD"));
        assertThat(result.triggered()).isTrue();
        assertThat(result.reason()).contains("15000");
    }

    @Test
    void doesNotTrigger_whenAmountEqualsThreshold() {
        assertThat(rule.evaluate(ctx("10000", "USD")).triggered()).isFalse();
    }

    @Test
    void usesPerCurrencyThreshold() {
        assertThat(rule.evaluate(ctx("20000", "JPY")).triggered()).isFalse();
        assertThat(rule.evaluate(ctx("2000000", "JPY")).triggered()).isTrue();
    }
}
