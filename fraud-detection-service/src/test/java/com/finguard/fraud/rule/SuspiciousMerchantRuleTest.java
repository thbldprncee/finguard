package com.finguard.fraud.rule;

import com.finguard.fraud.rule.impl.SuspiciousMerchantCategoryRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SuspiciousMerchantRuleTest {

    private final SuspiciousMerchantCategoryRule rule = new SuspiciousMerchantCategoryRule(TestFraudProperties.defaults());

    private RuleContext ctx(String category) {
        return RuleContext.builder()
                .transactionId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100)).country("US").currency("USD").paymentMethod("CARD")
                .merchantCategory(category).occurredAt(Instant.now())
                .build();
    }

    @Test
    void triggers_forGamblingCategory() {
        assertThat(rule.evaluate(ctx("GAMBLING")).triggered()).isTrue();
    }

    @Test
    void triggers_caseInsensitive() {
        assertThat(rule.evaluate(ctx("crypto")).triggered()).isTrue();
    }

    @Test
    void doesNotTrigger_forRetailCategory() {
        assertThat(rule.evaluate(ctx("RETAIL")).triggered()).isFalse();
    }

    @Test
    void doesNotTrigger_whenCategoryIsNull() {
        assertThat(rule.evaluate(ctx(null)).triggered()).isFalse();
    }
}
