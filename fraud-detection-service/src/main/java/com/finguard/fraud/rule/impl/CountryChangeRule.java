package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Remembers the last transaction id with its country and the country before it, so that
 * re-analysing the same transaction compares against the same previous country.
 * Events are keyed by userId, so one user's transactions are analysed sequentially.
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
        String key = "fraud:country:" + ctx.getUserId();
        String txId = ctx.getTransactionId().toString();
        HashOperations<String, String, String> hash = redis.opsForHash();
        Map<String, String> state = hash.entries(key);

        String previous = txId.equals(state.get("lastTx")) ? state.get("previous") : state.get("country");
        hash.putAll(key, Map.of(
                "lastTx", txId,
                "country", ctx.getCountry(),
                "previous", previous == null ? "" : previous));
        redis.expire(key, settings.memory());

        if (previous != null && !previous.isEmpty() && !previous.equalsIgnoreCase(ctx.getCountry())) {
            return RuleResult.triggered(CODE, String.format("Country changed from %s to %s", previous, ctx.getCountry()));
        }
        return RuleResult.notTriggered(CODE);
    }
}
