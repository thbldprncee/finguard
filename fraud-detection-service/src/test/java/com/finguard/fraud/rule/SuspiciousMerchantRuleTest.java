package com.finguard.fraud.rule;

import com.finguard.fraud.rule.impl.SuspiciousMerchantCategoryRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SuspiciousMerchantRuleTest {

    private final SuspiciousMerchantCategoryRule rule =
            new SuspiciousMerchantCategoryRule("GAMBLING,CRYPTO,ADULT", 20);

    private RuleContext buildContext(String category) {
        return RuleContext.builder()
                .transactionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100))
                .country("US").currency("USD")
                .paymentMethod("CARD")
                .merchantCategory(category)
                .occurredAt(Instant.now())
                .build();
    }

    @Test
    void triggers_forGamblingCategory() {
        RuleResult result = rule.evaluate(buildContext("GAMBLING"));
        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(20);
    }

    @Test
    void triggers_caseInsensitive() {
        assertThat(rule.evaluate(buildContext("crypto")).triggered()).isTrue();
    }

    @Test
    void doesNotTrigger_forRetailCategory() {
        assertThat(rule.evaluate(buildContext("RETAIL")).triggered()).isFalse();
    }

    @Test
    void doesNotTrigger_whenCategoryIsNull() {
        assertThat(rule.evaluate(buildContext(null)).triggered()).isFalse();
    }
}
