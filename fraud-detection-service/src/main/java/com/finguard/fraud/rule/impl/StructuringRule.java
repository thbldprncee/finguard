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
 * Detects splitting a large payment into several smaller ones to stay under HIGH_AMOUNT.
 * Members are "transactionId:amount", so a re-delivered event is not counted twice.
 */
@Component
public class StructuringRule implements FraudRuleEvaluator {

    public static final String CODE = "STRUCTURING";

    private final StringRedisTemplate redis;
    private final FraudProperties.Structuring settings;
    private final FraudProperties.HighAmount highAmount;

    public StructuringRule(StringRedisTemplate redis, FraudProperties properties) {
        this.redis = redis;
        this.settings = properties.rules().structuring();
        this.highAmount = properties.rules().highAmount();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        BigDecimal threshold = highAmount.thresholdFor(ctx.getCurrency());
        String key = "fraud:amounts:" + ctx.getUserId() + ":" + ctx.getCurrency();
        long at = ctx.getOccurredAt().toEpochMilli();
        long windowStart = at - settings.window().toMillis();
        ZSetOperations<String, String> zset = redis.opsForZSet();

        zset.add(key, ctx.getTransactionId() + ":" + ctx.getAmount().toPlainString(), at);
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
        Set<String> members = zset.rangeByScore(key, windowStart + 1, at);
        redis.expire(key, settings.window().multipliedBy(2));

        // A single amount above the threshold is HIGH_AMOUNT's job.
        if (members == null || ctx.getAmount().compareTo(threshold) > 0 || members.size() < settings.minTransactions()) {
            return RuleResult.notTriggered(CODE);
        }
        BigDecimal total = members.stream()
                .map(m -> new BigDecimal(m.substring(m.lastIndexOf(':') + 1)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(threshold) > 0) {
            return RuleResult.triggered(CODE, String.format("%d transactions totalling %s %s within %d hours exceed threshold %s",
                    members.size(), total.toPlainString(), ctx.getCurrency(), settings.window().toHours(), threshold.toPlainString()));
        }
        return RuleResult.notTriggered(CODE);
    }
}
