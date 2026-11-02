package com.finguard.fraud.ml;

import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import com.finguard.fraud.service.RuleConfigService;
import com.finguard.fraud.service.RuleEngineService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MlRuleTest {

    private final MlScorer scorer = mock(MlScorer.class);
    private final RuleConfigService config = mock(RuleConfigService.class);
    private final MlRule rule = new MlRule(scorer, config, new MlProperties(true, "classpath:ml/", 0.8));

    private final RuleContext ctx = RuleContext.builder().transactionId(UUID.randomUUID()).userId(UUID.randomUUID())
            .amount(new BigDecimal("120")).currency("USD").country("DE").occurredAt(Instant.now()).build();
    // One rule fired with weight 30.
    private final RuleEngineService.Evaluation rules = new RuleEngineService.Evaluation(
            List.of(RuleResult.triggered("HIGH_AMOUNT", "big").withScore(30), RuleResult.notTriggered("VELOCITY")), 30);

    private void model(Double probability) {
        when(scorer.available()).thenReturn(true);
        when(scorer.score(any())).thenReturn(probability == null ? Optional.empty()
                : Optional.of(new MlScorer.Score(probability, "m1")));
    }

    private void mlScoreRule(boolean enabled) {
        when(config.current()).thenReturn(Map.of(MlRule.CODE, new RuleConfigService.RuleSetting(enabled, 40)));
    }

    @Test
    void disabled_isShadowMode_scoreStoredButEvaluationUnchanged() {
        model(0.99);
        mlScoreRule(false);
        MlRule.Outcome out = rule.apply(ctx, rules);
        assertThat(out.evaluation()).isSameAs(rules);
        assertThat(out.score().probability()).isEqualTo(0.99);
        assertThat(out.features()).containsEntry("rule_HIGH_AMOUNT", 1.0).containsEntry("country", "DE");
    }

    @Test
    void enabled_aboveThreshold_triggersWithItsWeight() {
        model(0.85);
        mlScoreRule(true);
        RuleEngineService.Evaluation e = rule.apply(ctx, rules).evaluation();
        assertThat(e.riskScore()).isEqualTo(70);
        assertThat(e.triggered()).extracting(RuleResult::ruleCode).containsExactly("HIGH_AMOUNT", MlRule.CODE);
        assertThat(e.triggered().get(1).reason()).contains("0.85");
    }

    @Test
    void enabled_belowThreshold_doesNotTrigger() {
        model(0.79);
        mlScoreRule(true);
        RuleEngineService.Evaluation e = rule.apply(ctx, rules).evaluation();
        assertThat(e.riskScore()).isEqualTo(30);
        assertThat(e.results()).extracting(RuleResult::ruleCode).contains(MlRule.CODE);
        assertThat(e.failed()).isEmpty();
    }

    @Test
    void enabled_modelFailsToScore_ruleFails_soThePolicyEscalates() {
        model(null);
        mlScoreRule(true);
        assertThat(rule.apply(ctx, rules).evaluation().failed()).extracting(RuleResult::ruleCode).containsExactly(MlRule.CODE);
    }

    @Test
    void enabled_withoutAModel_isSkipped() {
        when(scorer.available()).thenReturn(false);
        when(scorer.score(any())).thenReturn(Optional.empty());
        mlScoreRule(true);
        assertThat(rule.apply(ctx, rules).evaluation()).isSameAs(rules);
    }
}
