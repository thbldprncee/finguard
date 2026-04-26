package com.finguard.fraud.service;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final List<FraudRuleEvaluator> rules;

    public List<RuleResult> evaluate(RuleContext context) {
        List<RuleResult> results = rules.stream()
                .map(rule -> {
                    try {
                        return rule.evaluate(context);
                    } catch (Exception e) {
                        log.error("Rule {} evaluation failed for tx={}: {}",
                                rule.getRuleCode(), context.getTransactionId(), e.getMessage(), e);
                        return RuleResult.notTriggered(rule.getRuleCode());
                    }
                })
                .toList();

        log.debug("Rule evaluation for tx={}: {} rules evaluated, {} triggered",
                context.getTransactionId(),
                results.size(),
                results.stream().filter(RuleResult::triggered).count());

        return results;
    }
}
