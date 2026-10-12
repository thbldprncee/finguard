package com.finguard.fraud.rule;

import com.finguard.fraud.rule.impl.NightActivityRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NightActivityRuleTest {

    private final NightActivityRule rule = new NightActivityRule(TestFraudProperties.defaults());

    private RuleContext at(String instant) {
        return RuleContext.builder()
                .transactionId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100)).country("US").currency("USD").paymentMethod("CARD")
                .occurredAt(Instant.parse(instant))
                .build();
    }

    @Test
    void triggers_atMidnight() {
        assertThat(rule.evaluate(at("2026-03-01T00:00:00Z")).triggered()).isTrue();
    }

    @Test
    void triggers_at3am() {
        assertThat(rule.evaluate(at("2026-03-01T03:30:00Z")).triggered()).isTrue();
    }

    @Test
    void doesNotTrigger_at9am() {
        assertThat(rule.evaluate(at("2026-03-01T09:00:00Z")).triggered()).isFalse();
    }

    @Test
    void doesNotTrigger_at5am() {
        assertThat(rule.evaluate(at("2026-03-01T05:00:00Z")).triggered()).isFalse();
    }

    @Test
    void usesTransactionTime_notAnalysisTime() {
        // Created at 02:00 UTC, analysed whenever: still night activity.
        assertThat(rule.evaluate(at("2020-01-01T02:00:00Z")).triggered()).isTrue();
    }

    @Test
    void supportsWindowAcrossMidnight_andTimeZone() {
        var wrapped = new NightActivityRule(TestFraudProperties.with(Map.of(
                "finguard.fraud.rules.night-activity.start-hour", "22",
                "finguard.fraud.rules.night-activity.end-hour", "4",
                "finguard.fraud.rules.night-activity.zone", "Europe/Moscow")));

        assertThat(wrapped.evaluate(at("2026-03-01T20:30:00Z")).triggered()).isTrue();  // 23:30 MSK
        assertThat(wrapped.evaluate(at("2026-03-01T00:30:00Z")).triggered()).isTrue();  // 03:30 MSK
        assertThat(wrapped.evaluate(at("2026-03-01T09:00:00Z")).triggered()).isFalse(); // 12:00 MSK
    }
}
