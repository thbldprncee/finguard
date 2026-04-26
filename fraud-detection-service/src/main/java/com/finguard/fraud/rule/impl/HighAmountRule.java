package com.finguard.fraud.rule.impl;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class HighAmountRule implements FraudRuleEvaluator {

    private final BigDecimal threshold;
    private final int score;

    public HighAmountRule(
            @Value("${fraud.rules.high-amount.threshold:10000}") double threshold,
            @Value("${fraud.rules.high-amount.score:30}") int score) {
        this.threshold = BigDecimal.valueOf(threshold);
        this.score = score;
    }

    @Override
    public String getRuleCode() {
        return "HIGH_AMOUNT";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        if (ctx.getAmount().compareTo(threshold) > 0) {
            return RuleResult.triggered(getRuleCode(), score,
                    String.format("Amount %.2f exceeds threshold %.2f", ctx.getAmount(), threshold));
        }
        return RuleResult.notTriggered(getRuleCode());
    }
}
