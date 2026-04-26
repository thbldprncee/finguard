package com.finguard.fraud.rule;

public record RuleResult(
        String ruleCode,
        boolean triggered,
        int scoreContribution,
        String reason
) {
    public static RuleResult notTriggered(String ruleCode) {
        return new RuleResult(ruleCode, false, 0, null);
    }

    public static RuleResult triggered(String ruleCode, int score, String reason) {
        return new RuleResult(ruleCode, true, score, reason);
    }
}
