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
public class CountryChangeRule implements FraudRuleEvaluator {

    private final StringRedisTemplate redisTemplate;
    private final int score;

    public CountryChangeRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.country-change.score:20}") int score) {
        this.redisTemplate = redisTemplate;
        this.score = score;
    }

    @Override
    public String getRuleCode() {
        return "COUNTRY_CHANGE";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        String key = "last_country:" + ctx.getUserId().toString();
        String lastCountry = redisTemplate.opsForValue().get(key);

        redisTemplate.opsForValue().set(key, ctx.getCountry(), Duration.ofHours(24));

        if (lastCountry != null && !lastCountry.equalsIgnoreCase(ctx.getCountry())) {
            return RuleResult.triggered(getRuleCode(), score,
                    String.format("Country changed from %s to %s", lastCountry, ctx.getCountry()));
        }
        return RuleResult.notTriggered(getRuleCode());
    }
}
