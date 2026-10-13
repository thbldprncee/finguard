package com.finguard.fraud.rule.impl;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A device is "new" for the user's earliest transaction from it, by creation time.
 * Per user, a sorted set maps deviceId -> earliest creation time seen; a Lua script lowers the
 * score atomically when an earlier transaction arrives late. A transaction is flagged when its
 * own time is that earliest time - so a re-delivered event gets the same answer, and the order
 * in which events are processed does not decide which transaction counts as the first one.
 */
@Component
public class NewDeviceRule implements FraudRuleEvaluator {

    public static final String CODE = "NEW_DEVICE";

    private static final DefaultRedisScript<String> RECORD_EARLIEST = new DefaultRedisScript<>("""
            local current = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if (not current) or (tonumber(ARGV[2]) < tonumber(current)) then
              redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
              current = ARGV[2]
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return tostring(current)
            """, String.class);

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

        long at = ctx.getOccurredAt().toEpochMilli();
        String earliest = redis.execute(RECORD_EARLIEST, List.of("fraud:devices:" + ctx.getUserId()),
                ctx.getDeviceId(), String.valueOf(at), String.valueOf(settings.memory().toMillis()));
        if (earliest != null && (long) Double.parseDouble(earliest) == at) {
            return RuleResult.triggered(CODE, "Transaction from a new device");
        }
        return RuleResult.notTriggered(CODE);
    }
}
