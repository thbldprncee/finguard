package com.finguard.fraud.service;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rules are plain Spring beans: adding a rule means adding a FraudRuleEvaluator and a
 * fraud_rules row; the engine itself does not change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final List<FraudRuleEvaluator> rules;
    private final RuleConfigService ruleConfigService;

    public record Evaluation(List<RuleResult> results, int riskScore) {

        public List<RuleResult> triggered() {
            return results.stream().filter(RuleResult::triggered).toList();
        }

        public List<RuleResult> failed() {
            return results.stream().filter(RuleResult::failed).toList();
        }
    }

    public Evaluation evaluate(RuleContext context) {
        Map<String, RuleConfigService.RuleSetting> settings = ruleConfigService.current();
        List<RuleResult> results = new ArrayList<>(rules.size());

        for (FraudRuleEvaluator rule : rules) {
            String code = rule.getRuleCode();
            RuleConfigService.RuleSetting setting = settings.get(code);
            if (setting == null) {
                results.add(RuleResult.failed(code, "Rule configuration missing"));
                continue;
            }
            if (!setting.enabled()) {
                results.add(RuleResult.disabled(code));
                continue;
            }
            RuleResult result;
            try {
                result = rule.evaluate(context);
            } catch (Exception e) {
                // Not "not triggered": a broken check must not lower the risk score (fail-open).
                log.error("Rule {} evaluation failed for tx={}: {}", code, context.getTransactionId(), e.getMessage(), e);
                result = RuleResult.failed(code, "Rule could not be evaluated");
            }
            results.add(result.triggered() ? result.withScore(setting.weight()) : result);
        }

        int score = Math.min(100, results.stream().mapToInt(RuleResult::scoreContribution).sum());
        log.debug("Rule evaluation for tx={}: score={}, triggered={}, failed={}",
                context.getTransactionId(), score,
                results.stream().filter(RuleResult::triggered).count(),
                results.stream().filter(RuleResult::failed).count());
        return new Evaluation(List.copyOf(results), score);
    }
}
