package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.stereotype.Component;

@Component
public class NightActivityRule implements FraudRuleEvaluator {

    public static final String CODE = "NIGHT_ACTIVITY";

    private final FraudProperties.NightActivity settings;

    public NightActivityRule(FraudProperties properties) {
        this.settings = properties.rules().nightActivity();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        int hour = ctx.getOccurredAt().atZone(settings.zone()).getHour();
        int start = settings.startHour();
        int end = settings.endHour();
        boolean night = start <= end ? hour >= start && hour < end : hour >= start || hour < end;
        if (night) {
            return RuleResult.triggered(CODE, String.format("Transaction at unusual hour: %02d:00 %s", hour, settings.zone()));
        }
        return RuleResult.notTriggered(CODE);
    }
}
