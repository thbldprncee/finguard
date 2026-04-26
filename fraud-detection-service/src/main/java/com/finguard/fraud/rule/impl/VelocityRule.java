package com.finguard.fraud.rule.impl;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class VelocityRule implements FraudRuleEvaluator {

    private final StringRedisTemplate redisTemplate;
    private final int maxTransactions;
    private final int windowMinutes;
    private final int score;

    public VelocityRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.velocity.max-transactions:5}") int maxTransactions,
            @Value("${fraud.rules.velocity.window-minutes:10}") int windowMinutes,
            @Value("${fraud.rules.velocity.score:25}") int score) {
        this.redisTemplate = redisTemplate;
        this.maxTransactions = maxTransactions;
        this.windowMinutes = windowMinutes;
        this.score = score;
    }

    @Override
    public String getRuleCode() {
        return "VELOCITY";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        String key = "velocity:" + ctx.getUserId().toString();
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
        }

        if (count != null && count > maxTransactions) {
            return RuleResult.triggered(getRuleCode(), score,
                    String.format("User made %d transactions in %d minutes (max %d)", count, windowMinutes, maxTransactions));
        }
        return RuleResult.notTriggered(getRuleCode());
    }
}
