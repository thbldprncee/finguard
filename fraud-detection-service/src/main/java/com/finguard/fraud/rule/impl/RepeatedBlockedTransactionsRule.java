package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Blocked transactions are recorded in a sorted set (member = transactionId) only after the
 * analysis is committed, so a retried or rolled-back analysis does not inflate the count.
 */
@Component
public class RepeatedBlockedTransactionsRule implements FraudRuleEvaluator {

    public static final String CODE = "REPEATED_BLOCKED";

    private final StringRedisTemplate redis;
    private final FraudProperties.RepeatedBlocked settings;

    public RepeatedBlockedTransactionsRule(StringRedisTemplate redis, FraudProperties properties) {
        this.redis = redis;
        this.settings = properties.rules().repeatedBlocked();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        long at = ctx.getOccurredAt().toEpochMilli();
        Long count = redis.opsForZSet().count(key(ctx.getUserId()), at - settings.lookback().toMillis(), at - 1);
        if (count != null && count >= settings.minCount()) {
            return RuleResult.triggered(CODE, String.format("%d blocked transactions in the last %d hours",
                    count, settings.lookback().toHours()));
        }
        return RuleResult.notTriggered(CODE);
    }

    public void recordBlocked(UUID userId, UUID transactionId, Instant occurredAt) {
        String key = key(userId);
        long at = occurredAt.toEpochMilli();
        redis.opsForZSet().add(key, transactionId.toString(), at);
        redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, at - settings.lookback().toMillis());
        redis.expire(key, settings.lookback().multipliedBy(2));
    }

    private static String key(UUID userId) {
        return "fraud:blocked:" + userId;
    }
}
