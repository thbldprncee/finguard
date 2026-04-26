package com.finguard.fraud.rule;

import com.finguard.fraud.rule.impl.NightActivityRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NightActivityRuleTest {

    private final NightActivityRule rule = new NightActivityRule(0, 5, 15);

    private RuleContext buildContextAtHour(int hourUtc) {
        Instant at = ZonedDateTime.now(ZoneOffset.UTC)
                .withHour(hourUtc).withMinute(0).withSecond(0).toInstant();
        return RuleContext.builder()
                .transactionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100))
                .country("US").currency("USD")
                .paymentMethod("CARD")
                .occurredAt(at)
                .build();
    }

    @Test
    void triggers_atMidnight() {
        assertThat(rule.evaluate(buildContextAtHour(0)).triggered()).isTrue();
    }

    @Test
    void triggers_at3am() {
        assertThat(rule.evaluate(buildContextAtHour(3)).triggered()).isTrue();
    }

    @Test
    void doesNotTrigger_at9am() {
        assertThat(rule.evaluate(buildContextAtHour(9)).triggered()).isFalse();
    }

    @Test
    void doesNotTrigger_at5am() {
        assertThat(rule.evaluate(buildContextAtHour(5)).triggered()).isFalse();
    }
}
