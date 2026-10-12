package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The key stores the id of the first transaction seen from the device. Re-analysing that same
 * transaction (Kafka re-delivery) still reports "new device" instead of flipping to "known".
 */
@Component
public class NewDeviceRule implements FraudRuleEvaluator {

    public static final String CODE = "NEW_DEVICE";

    private final StringRedisTemplate redis;
    private final FraudProperties.NewDevice settings;

    public NewDeviceRule(StringRedisTemplate redis, FraudProperties properties) {
        this.redis = redis;
        this.settings = properties.rules().newDevice();
    }

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        if (ctx.getDeviceId() == null || ctx.getDeviceId().isBlank()) {
            return settings.treatMissingAsRisky()
                    ? RuleResult.triggered(CODE, "Transaction without a device id")
                    : RuleResult.notTriggered(CODE);
        }

        String key = "fraud:device:" + ctx.getUserId() + ":" + ctx.getDeviceId();
        String txId = ctx.getTransactionId().toString();
        Boolean firstSeen = redis.opsForValue().setIfAbsent(key, txId, settings.memory());
        if (Boolean.TRUE.equals(firstSeen) || txId.equals(redis.opsForValue().get(key))) {
            return RuleResult.triggered(CODE, "Transaction from a new device");
        }
        redis.expire(key, settings.memory());
        return RuleResult.notTriggered(CODE);
    }
}
