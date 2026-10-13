package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Detects splitting a large payment into several smaller ones to stay under HIGH_AMOUNT:
 * at least N transactions, each at or below the threshold, that together exceed it.
 * Only qualifying (below-threshold) amounts are stored, so a large payment - HIGH_AMOUNT's job -
 * never counts towards structuring. Members are "transactionId:amount", so a re-delivered event
 * is counted once; scores are creation times, so the result does not depend on processing order.
 */
@Component
public class StructuringRule implements FraudRuleEvaluator {

    public static final String CODE = "STRUCTURING";

    private final StringRedisTemplate redis;
    private final FraudProperties.Structuring settings;
    private final FraudProperties.HighAmount highAmount;
    private final long retentionMillis;

    public StructuringRule(StringRedisTemplate redis, FraudProperties properties) {
        this.redis = redis;
        this.settings = properties.rules().structuring();
        this.highAmount = properties.rules().highAmount();
        this.retentionMillis = settings.window().plus(properties.maxEventLateness()).toMillis();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        BigDecimal threshold = highAmount.thresholdFor(ctx.getCurrency());
        if (ctx.getAmount().compareTo(threshold) > 0) {
            return RuleResult.notTriggered(CODE);
        }

        String key = "fraud:amounts:" + ctx.getUserId() + ":" + ctx.getCurrency();
        long at = ctx.getOccurredAt().toEpochMilli();
        long windowStart = at - settings.window().toMillis();
        ZSetOperations<String, String> zset = redis.opsForZSet();

        zset.add(key, ctx.getTransactionId() + ":" + ctx.getAmount().toPlainString(), at);
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, at - retentionMillis);
        Set<String> members = zset.rangeByScore(key, windowStart + 1, at);
        redis.expire(key, java.time.Duration.ofMillis(retentionMillis * 2));

        if (members == null || members.size() < settings.minTransactions()) {
            return RuleResult.notTriggered(CODE);
        }
        BigDecimal total = members.stream()
                .map(m -> new BigDecimal(m.substring(m.lastIndexOf(':') + 1)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(threshold) > 0) {
            return RuleResult.triggered(CODE, String.format("%d transactions below %s %s totalling %s within %d hours",
                    members.size(), threshold.toPlainString(), ctx.getCurrency(), total.toPlainString(), settings.window().toHours()));
        }
        return RuleResult.notTriggered(CODE);
    }
}
