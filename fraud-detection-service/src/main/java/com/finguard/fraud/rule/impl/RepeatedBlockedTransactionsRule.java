package com.finguard.fraud.rule.impl;

import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepeatedBlockedTransactionsRule implements FraudRuleEvaluator {

    private final StringRedisTemplate redisTemplate;

    @Value("${fraud.rules.repeated-blocked.lookback-hours:24}")
    private int lookbackHours;

    @Value("${fraud.rules.repeated-blocked.min-count:1}")
    private int minCount;

    @Value("${fraud.rules.repeated-blocked.score:25}")
    private int score;

    @Override
    public String getRuleCode() {
        return "REPEATED_BLOCKED";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        String redisKey = "blocked_count:" + ctx.getUserId();
        String raw = redisTemplate.opsForValue().get(redisKey);
        long blockedCount = raw != null ? Long.parseLong(raw) : 0;

        if (blockedCount >= minCount) {
            return RuleResult.triggered(getRuleCode(), score,
                    String.format("User had %d blocked transactions in the last %d hours", blockedCount, lookbackHours));
        }
        return RuleResult.notTriggered(getRuleCode());
    }
}
