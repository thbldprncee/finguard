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
 * The count covers (creation time - window, creation time], so it is the same whatever order
 * the events are processed in; entries are kept for window + max lateness for late events.
 */
@Component
public class VelocityRule implements FraudRuleEvaluator {

    public static final String CODE = "VELOCITY";
    /** Transactions of the user in the velocity window, this one included. */
    public static final String SIGNAL_TX_IN_WINDOW = "velocity_tx_in_window";

    private final StringRedisTemplate redis;
    private final FraudProperties.Velocity settings;
    private final long retentionMillis;

    public VelocityRule(StringRedisTemplate redis, FraudProperties properties) {
        this.redis = redis;
        this.settings = properties.rules().velocity();
        this.retentionMillis = settings.window().plus(properties.maxEventLateness()).toMillis();
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
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, at - retentionMillis);
        Long count = zset.count(key, windowStart + 1, at);
        redis.expire(key, java.time.Duration.ofMillis(retentionMillis * 2));

        long inWindow = count == null ? 0 : count;
        RuleResult result = inWindow > settings.maxTransactions()
                ? RuleResult.triggered(CODE, String.format("%d transactions within %d minutes (max %d)",
                        inWindow, settings.window().toMinutes(), settings.maxTransactions()))
                : RuleResult.notTriggered(CODE);
        return result.withSignal(SIGNAL_TX_IN_WINDOW, inWindow);
    }
}
