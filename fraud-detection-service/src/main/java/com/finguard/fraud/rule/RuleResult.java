package com.finguard.fraud.rule;

import java.util.Map;

/**
 * Rules only say whether they fired and why. The score a triggered rule contributes is its
 * weight from the fraud_rules table, applied by {@link com.finguard.fraud.service.RuleEngineService}.
 * <p>
 * Signals are numbers a rule computed anyway (e.g. how many transactions were in the velocity
 * window). They do not affect the rule's outcome; the ML model uses them as features, so it
 * sees "4 of 5" and not only "not triggered".
 */
public record RuleResult(
        String ruleCode,
        Outcome outcome,
        int scoreContribution,
        String reason,
        Map<String, Double> signals
) {
    public enum Outcome {
        TRIGGERED,
        NOT_TRIGGERED,
        /** Could not be evaluated (technical error). Never silently counted as "not triggered". */
        FAILED,
        DISABLED
    }

    public RuleResult {
        signals = signals == null ? Map.of() : Map.copyOf(signals);
    }

    public static RuleResult notTriggered(String ruleCode) {
        return new RuleResult(ruleCode, Outcome.NOT_TRIGGERED, 0, null, null);
    }

    public static RuleResult triggered(String ruleCode, String reason) {
        return new RuleResult(ruleCode, Outcome.TRIGGERED, 0, reason, null);
    }

    public static RuleResult failed(String ruleCode, String reason) {
        return new RuleResult(ruleCode, Outcome.FAILED, 0, reason, null);
    }

    public static RuleResult disabled(String ruleCode) {
        return new RuleResult(ruleCode, Outcome.DISABLED, 0, null, null);
    }

    public boolean triggered() {
        return outcome == Outcome.TRIGGERED;
    }

    public boolean failed() {
        return outcome == Outcome.FAILED;
    }

    public RuleResult withScore(int score) {
        return new RuleResult(ruleCode, outcome, score, reason, signals);
    }

    public RuleResult withSignal(String name, double value) {
        Map<String, Double> next = new java.util.HashMap<>(signals);
        next.put(name, value);
        return new RuleResult(ruleCode, outcome, scoreContribution, reason, next);
    }
}
