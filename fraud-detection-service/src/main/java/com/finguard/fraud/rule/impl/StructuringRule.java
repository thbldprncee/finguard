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
 * <p>
 * The window is read newest first in pages and reading stops as soon as the rule has triggered,
 * so the cost depends on the threshold, not on how many payments the user made in the window.
 * Loading the whole window made every transaction of a very active account more expensive
 * (thousands of members per call) and slowed down the whole consumer.
 */
@Component
public class StructuringRule implements FraudRuleEvaluator {

    public static final String CODE = "STRUCTURING";
    /** Below-threshold payments of the user in the window, this one included; 0 for a large payment. */
    public static final String SIGNAL_SMALL_PAYMENTS_IN_WINDOW = "structuring_payments_in_window";
    private static final int PAGE_SIZE = 50;

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
        redis.expire(key, java.time.Duration.ofMillis(retentionMillis * 2));
        Long inWindow = zset.count(key, windowStart + 1, at);
        long payments = inWindow == null ? 0 : inWindow;
        if (payments < settings.minTransactions()) {
            return RuleResult.notTriggered(CODE).withSignal(SIGNAL_SMALL_PAYMENTS_IN_WINDOW, payments);
        }

        BigDecimal total = BigDecimal.ZERO;
        int counted = 0;
        for (long offset = 0; offset < payments; offset += PAGE_SIZE) {
            Set<String> page = zset.reverseRangeByScore(key, windowStart + 1, at, offset, PAGE_SIZE);
            if (page == null || page.isEmpty()) {
                break;
            }
            for (String member : page) {
                total = total.add(new BigDecimal(member.substring(member.lastIndexOf(':') + 1)));
                counted++;
                if (counted >= settings.minTransactions() && total.compareTo(threshold) > 0) {
                    return RuleResult.triggered(CODE, String.format("%d transactions below %s %s totalling %s within %d hours",
                            counted, threshold.toPlainString(), ctx.getCurrency(), total.toPlainString(), settings.window().toHours()))
                            .withSignal(SIGNAL_SMALL_PAYMENTS_IN_WINDOW, payments);
                }
            }
        }
        return RuleResult.notTriggered(CODE).withSignal(SIGNAL_SMALL_PAYMENTS_IN_WINDOW, payments);
    }
}
