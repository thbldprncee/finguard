package com.finguard.fraud.decision;

import com.finguard.fraud.config.FraudProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DecisionConfig {

    /** Picks the failure strategy once, instead of switching on the policy at every decision. */
    @Bean
    public RuleFailurePolicy ruleFailurePolicy(FraudProperties properties) {
        return switch (properties.decision().onRuleFailure()) {
            case REVIEW -> new RuleFailurePolicy.ReviewOnFailure();
            case BLOCK -> new RuleFailurePolicy.BlockOnFailure();
        };
    }
}
