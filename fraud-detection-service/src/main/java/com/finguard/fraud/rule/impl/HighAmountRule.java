package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class HighAmountRule implements FraudRuleEvaluator {

    public static final String CODE = "HIGH_AMOUNT";

    private final FraudProperties.HighAmount settings;

    public HighAmountRule(FraudProperties properties) {
        this.settings = properties.rules().highAmount();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        BigDecimal threshold = settings.thresholdFor(ctx.getCurrency());
        if (ctx.getAmount().compareTo(threshold) > 0) {
            return RuleResult.triggered(CODE, String.format("Amount %s %s exceeds threshold %s",
                    ctx.getAmount().toPlainString(), ctx.getCurrency(), threshold.toPlainString()));
        }
        return RuleResult.notTriggered(CODE);
    }
}
