package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Compares with the country of the user's previous transaction by creation time, not by
 * processing order: transactions are kept in a sorted set (member = "transactionId|country",
 * score = creation time). A late event (retry topic, outbox retry) is compared with its real
 * predecessor, and a re-delivered event finds the same predecessor again.
 */
@Component
public class CountryChangeRule implements FraudRuleEvaluator {

    public static final String CODE = "COUNTRY_CHANGE";

    private final StringRedisTemplate redis;
    private final FraudProperties.CountryChange settings;

    public CountryChangeRule(StringRedisTemplate redis, FraudProperties properties) {
        this.redis = redis;
        this.settings = properties.rules().countryChange();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        String key = "fraud:countries:" + ctx.getUserId();
        long at = ctx.getOccurredAt().toEpochMilli();
        String self = ctx.getTransactionId() + "|" + ctx.getCountry();
        ZSetOperations<String, String> zset = redis.opsForZSet();

        zset.add(key, self, at);
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, at - settings.memory().toMillis());
        redis.expire(key, settings.memory());

        String previous = previousCountry(zset, key, at, self);
        if (previous != null && !previous.equalsIgnoreCase(ctx.getCountry())) {
            return RuleResult.triggered(CODE, String.format("Country changed from %s to %s", previous, ctx.getCountry()));
        }
        return RuleResult.notTriggered(CODE);
    }

    /** Latest transaction created before this one (same-millisecond entries other than itself count too). */
    private static String previousCountry(ZSetOperations<String, String> zset, String key, long at, String self) {
        Set<String> candidates = zset.reverseRangeByScore(key, Double.NEGATIVE_INFINITY, at, 0, 2);
        if (candidates == null) {
            return null;
        }
        return candidates.stream()
                .filter(member -> !member.equals(self))
                .findFirst()
                .map(member -> member.substring(member.lastIndexOf('|') + 1))
                .orElse(null);
    }
}
