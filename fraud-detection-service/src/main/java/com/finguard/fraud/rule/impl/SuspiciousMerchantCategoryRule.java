package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class SuspiciousMerchantCategoryRule implements FraudRuleEvaluator {

    public static final String CODE = "SUSPICIOUS_MERCHANT";

    private final Set<String> suspiciousCategories;

    public SuspiciousMerchantCategoryRule(FraudProperties properties) {
        this.suspiciousCategories = properties.rules().suspiciousMerchant().categories();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        String category = ctx.getMerchantCategory();
        if (category != null && suspiciousCategories.contains(category.toUpperCase(Locale.ROOT))) {
            return RuleResult.triggered(CODE, "Suspicious merchant category: " + category);
        }
        return RuleResult.notTriggered(CODE);
    }
}
