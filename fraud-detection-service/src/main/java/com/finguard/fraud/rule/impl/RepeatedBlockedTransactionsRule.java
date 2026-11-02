package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.repository.FraudAnalysisRepository;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Counts the user's BLOCKED decisions in fraud_analyses (indexed by user, decision, creation time).
 * Reading the source of truth instead of a Redis copy means a committed block can never be
 * missing from the history, and the count is by transaction creation time, so it does not depend
 * on the order in which events are processed.
 */
@Component
public class RepeatedBlockedTransactionsRule implements FraudRuleEvaluator {

    public static final String CODE = "REPEATED_BLOCKED";

    private final FraudAnalysisRepository repository;
    private final FraudProperties.RepeatedBlocked settings;

    public RepeatedBlockedTransactionsRule(FraudAnalysisRepository repository, FraudProperties properties) {
        this.repository = repository;
        this.settings = properties.rules().repeatedBlocked();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        Instant at = ctx.getOccurredAt();
        // Only whether min-count is reached matters, so stop counting there.
        long count = repository.countBlockedUpTo(
                ctx.getUserId(), at.minus(settings.lookback()), at, settings.minCount());
        if (count >= settings.minCount()) {
            return RuleResult.triggered(CODE, String.format("at least %d blocked transactions in the last %d hours",
                    count, settings.lookback().toHours()));
        }
        return RuleResult.notTriggered(CODE);
    }
}
