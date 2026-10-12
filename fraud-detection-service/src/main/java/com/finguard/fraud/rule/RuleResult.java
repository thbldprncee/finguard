package com.finguard.fraud.rule;

/**
 * Rules only say whether they fired and why. The score a triggered rule contributes is its
 * weight from the fraud_rules table, applied by {@link com.finguard.fraud.service.RuleEngineService}.
 */
public record RuleResult(
        String ruleCode,
        Outcome outcome,
        int scoreContribution,
        String reason
) {
    public enum Outcome {
        TRIGGERED,
        NOT_TRIGGERED,
        /** Could not be evaluated (technical error). Never silently counted as "not triggered". */
        FAILED,
        DISABLED
    }

    public static RuleResult notTriggered(String ruleCode) {
        return new RuleResult(ruleCode, Outcome.NOT_TRIGGERED, 0, null);
    }

    public static RuleResult triggered(String ruleCode, String reason) {
        return new RuleResult(ruleCode, Outcome.TRIGGERED, 0, reason);
    }

    public static RuleResult failed(String ruleCode, String reason) {
        return new RuleResult(ruleCode, Outcome.FAILED, 0, reason);
    }

    public static RuleResult disabled(String ruleCode) {
        return new RuleResult(ruleCode, Outcome.DISABLED, 0, null);
    }

    public boolean triggered() {
        return outcome == Outcome.TRIGGERED;
    }

    public boolean failed() {
        return outcome == Outcome.FAILED;
    }

    public RuleResult withScore(int score) {
        return new RuleResult(ruleCode, outcome, score, reason);
    }
}
