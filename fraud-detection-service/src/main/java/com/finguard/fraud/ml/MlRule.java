package com.finguard.fraud.ml;

import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import com.finguard.fraud.service.RuleConfigService;
import com.finguard.fraud.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Scores a transaction with the ML model and, when the ML_SCORE rule is enabled in fraud_rules,
 * adds it to the evaluation like any other rule: triggered at {@code finguard.fraud.ml.threshold},
 * contributing its weight. Disabled (the default), the score is only stored: shadow mode.
 * <p>
 * Runs after the other rules because their outcomes are model features. With ML_SCORE enabled,
 * a loaded model that fails to score makes the rule FAILED, so the failure policy escalates the
 * decision as for any rule; without a model at all the rule is skipped.
 */
@Component
@RequiredArgsConstructor
public class MlRule {

    public static final String CODE = "ML_SCORE";

    public record Outcome(RuleEngineService.Evaluation evaluation, Map<String, Object> features, MlScorer.Score score) {
    }

    private final MlScorer scorer;
    private final RuleConfigService ruleConfig;
    private final MlProperties properties;

    public Outcome apply(RuleContext context, RuleEngineService.Evaluation evaluation) {
        Map<String, Object> features = MlFeatures.of(context, evaluation);
        MlScorer.Score score = scorer.score(features).orElse(null);

        RuleConfigService.RuleSetting setting = ruleConfig.current().get(CODE);
        if (setting == null || !setting.enabled() || !scorer.available()) {
            return new Outcome(evaluation, features, score);
        }
        RuleResult result;
        if (score == null) {
            result = RuleResult.failed(CODE, "ML model could not score the transaction");
        } else if (score.probability() >= properties.threshold()) {
            result = RuleResult.triggered(CODE, String.format("ML model %s: fraud probability %.2f",
                    score.modelVersion(), score.probability())).withScore(setting.weight());
        } else {
            result = RuleResult.notTriggered(CODE);
        }
        return new Outcome(evaluation.plus(result), features, score);
    }
}
