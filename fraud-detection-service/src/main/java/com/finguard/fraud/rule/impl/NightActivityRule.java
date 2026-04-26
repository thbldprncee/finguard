package com.finguard.fraud.rule.impl;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class NightActivityRule implements FraudRuleEvaluator {

    private final int startHour;
    private final int endHour;
    private final int score;

    public NightActivityRule(
            @Value("${fraud.rules.night-activity.start-hour:0}") int startHour,
            @Value("${fraud.rules.night-activity.end-hour:5}") int endHour,
            @Value("${fraud.rules.night-activity.score:15}") int score) {
        this.startHour = startHour;
        this.endHour = endHour;
        this.score = score;
    }

    @Override
    public String getRuleCode() {
        return "NIGHT_ACTIVITY";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        int hour = ctx.getOccurredAt().atZone(ZoneOffset.UTC).getHour();
        if (hour >= startHour && hour < endHour) {
            return RuleResult.triggered(getRuleCode(), score,
                    String.format("Transaction at unusual hour: %02d:00 UTC", hour));
        }
        return RuleResult.notTriggered(getRuleCode());
    }
}
