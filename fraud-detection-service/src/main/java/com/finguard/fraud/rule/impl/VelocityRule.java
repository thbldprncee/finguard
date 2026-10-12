package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

/**
 * Sliding window over a Redis sorted set: member = transactionId, score = creation time.
 * Re-delivery of the same event re-adds the same member, so it is counted once
 * (an INCR counter would count every delivery). A fixed INCR+EXPIRE window also resets
 * at its boundary, letting 2x the limit through around it; the sliding window does not.
 */
@Component
public class VelocityRule implements FraudRuleEvaluator {

    public static final String CODE = "VELOCITY";

    private final StringRedisTemplate redis;
    private final FraudProperties.Velocity settings;

    public VelocityRule(StringRedisTemplate redis, FraudProperties properties) {
        this.redis = redis;
        this.settings = properties.rules().velocity();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        String key = "fraud:velocity:" + ctx.getUserId();
        long at = ctx.getOccurredAt().toEpochMilli();
        long windowStart = at - settings.window().toMillis();
        ZSetOperations<String, String> zset = redis.opsForZSet();

        zset.add(key, ctx.getTransactionId().toString(), at);
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
        Long count = zset.count(key, windowStart + 1, at);
        redis.expire(key, settings.window().multipliedBy(2));

        if (count != null && count > settings.maxTransactions()) {
            return RuleResult.triggered(CODE, String.format("%d transactions within %d minutes (max %d)",
                    count, settings.window().toMinutes(), settings.maxTransactions()));
        }
        return RuleResult.notTriggered(CODE);
    }
}
