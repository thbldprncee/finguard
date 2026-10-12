package com.finguard.fraud.service;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleEngineServiceTest {

    private final RuleContext ctx = RuleContext.builder()
            .transactionId(UUID.randomUUID()).userId(UUID.randomUUID())
            .amount(BigDecimal.TEN).currency("USD").country("US").occurredAt(Instant.now()).build();

    private static FraudRuleEvaluator rule(String code, RuleResult result, AtomicInteger calls) {
        return new FraudRuleEvaluator() {
            @Override public String getRuleCode() { return code; }
            @Override public RuleResult evaluate(RuleContext c) { calls.incrementAndGet(); return result; }
        };
    }

    private static FraudRuleEvaluator failing(String code) {
        return new FraudRuleEvaluator() {
            @Override public String getRuleCode() { return code; }
            @Override public RuleResult evaluate(RuleContext c) { throw new IllegalStateException("Redis down"); }
        };
    }

    private RuleEngineService engine(List<FraudRuleEvaluator> rules, Map<String, RuleConfigService.RuleSetting> settings) {
        RuleConfigService config = mock(RuleConfigService.class);
        when(config.current()).thenReturn(settings);
        return new RuleEngineService(rules, config);
    }

    @Test
    void appliesWeightFromDatabase() {
        var engine = engine(List.of(rule("A", RuleResult.triggered("A", "why"), new AtomicInteger())),
                Map.of("A", new RuleConfigService.RuleSetting(true, 42)));

        var evaluation = engine.evaluate(ctx);

        assertThat(evaluation.riskScore()).isEqualTo(42);
        assertThat(evaluation.triggered()).singleElement().extracting(RuleResult::scoreContribution).isEqualTo(42);
    }

    @Test
    void disabledRule_isNotEvaluated() {
        AtomicInteger calls = new AtomicInteger();
        var engine = engine(List.of(rule("A", RuleResult.triggered("A", "why"), calls)),
                Map.of("A", new RuleConfigService.RuleSetting(false, 42)));

        var evaluation = engine.evaluate(ctx);

        assertThat(calls).hasValue(0);
        assertThat(evaluation.riskScore()).isZero();
        assertThat(evaluation.results()).singleElement().extracting(RuleResult::outcome).isEqualTo(RuleResult.Outcome.DISABLED);
    }

    @Test
    void exception_isReportedAsFailed_notAsNotTriggered() {
        var engine = engine(List.of(failing("VELOCITY")), Map.of("VELOCITY", new RuleConfigService.RuleSetting(true, 25)));

        var evaluation = engine.evaluate(ctx);

        assertThat(evaluation.failed()).singleElement().extracting(RuleResult::ruleCode).isEqualTo("VELOCITY");
    }

    @Test
    void missingConfiguration_isFailed() {
        var engine = engine(List.of(rule("A", RuleResult.notTriggered("A"), new AtomicInteger())), Map.of());

        assertThat(engine.evaluate(ctx).failed()).hasSize(1);
    }

    @Test
    void scoreIsCappedAt100() {
        var engine = engine(List.of(
                        rule("A", RuleResult.triggered("A", "a"), new AtomicInteger()),
                        rule("B", RuleResult.triggered("B", "b"), new AtomicInteger())),
                Map.of("A", new RuleConfigService.RuleSetting(true, 80), "B", new RuleConfigService.RuleSetting(true, 80)));

        assertThat(engine.evaluate(ctx).riskScore()).isEqualTo(100);
    }
}
