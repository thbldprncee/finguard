package com.finguard.fraud.rule;

import com.finguard.fraud.rule.impl.HighAmountRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HighAmountRuleTest {

    private final HighAmountRule rule = new HighAmountRule(10000.0, 30);

    private RuleContext buildContext(double amount) {
        return RuleContext.builder()
                .transactionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(amount))
                .country("US").currency("USD")
                .paymentMethod("CARD")
                .occurredAt(Instant.now())
                .build();
    }

    @Test
    void doesNotTrigger_whenAmountBelowThreshold() {
        RuleResult result = rule.evaluate(buildContext(5000.0));
        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreContribution()).isZero();
    }

    @Test
    void triggers_whenAmountExceedsThreshold() {
        RuleResult result = rule.evaluate(buildContext(15000.0));
        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(30);
        assertThat(result.reason()).contains("15000");
    }

    @Test
    void doesNotTrigger_whenAmountEqualsThreshold() {
        RuleResult result = rule.evaluate(buildContext(10000.0));
        assertThat(result.triggered()).isFalse();
    }
}
