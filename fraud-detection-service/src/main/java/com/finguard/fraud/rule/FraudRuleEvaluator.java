package com.finguard.fraud.rule;

public interface FraudRuleEvaluator {

    String getRuleCode();

    RuleResult evaluate(RuleContext context);
}
