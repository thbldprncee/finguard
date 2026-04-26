package com.finguard.fraud.rule.impl;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class NewDeviceRule implements FraudRuleEvaluator {

    private final StringRedisTemplate redisTemplate;
    private final int score;

    public NewDeviceRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.new-device.score:15}") int score) {
        this.redisTemplate = redisTemplate;
        this.score = score;
    }

    @Override
    public String getRuleCode() {
        return "NEW_DEVICE";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        if (ctx.getDeviceId() == null || ctx.getDeviceId().isBlank()) {
            return RuleResult.notTriggered(getRuleCode());
        }

        String key = "known_device:" + ctx.getUserId() + ":" + ctx.getDeviceId();
        Boolean isKnown = redisTemplate.hasKey(key);

        if (Boolean.FALSE.equals(isKnown)) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofDays(30));
            return RuleResult.triggered(getRuleCode(), score,
                    "Transaction from new device: " + ctx.getDeviceId());
        }

        redisTemplate.expire(key, Duration.ofDays(30));
        return RuleResult.notTriggered(getRuleCode());
    }
}
