package com.finguard.fraud.rule.impl;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SuspiciousMerchantCategoryRule implements FraudRuleEvaluator {

    private final Set<String> suspiciousCategories;
    private final int score;

    public SuspiciousMerchantCategoryRule(
            @Value("${fraud.rules.suspicious-merchant.categories:GAMBLING,CRYPTO,ADULT}") String categories,
            @Value("${fraud.rules.suspicious-merchant.score:20}") int score) {
        this.suspiciousCategories = Arrays.stream(categories.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        this.score = score;
    }

    @Override
    public String getRuleCode() {
        return "SUSPICIOUS_MERCHANT";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        if (ctx.getMerchantCategory() != null &&
                suspiciousCategories.contains(ctx.getMerchantCategory().toUpperCase())) {
            return RuleResult.triggered(getRuleCode(), score,
                    "Suspicious merchant category: " + ctx.getMerchantCategory());
        }
        return RuleResult.notTriggered(getRuleCode());
    }
}
