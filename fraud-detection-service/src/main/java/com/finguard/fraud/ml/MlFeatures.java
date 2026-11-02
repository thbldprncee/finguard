package com.finguard.fraud.ml;

import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import com.finguard.fraud.rule.impl.HighAmountRule;
import com.finguard.fraud.rule.impl.StructuringRule;
import com.finguard.fraud.rule.impl.VelocityRule;
import com.finguard.fraud.service.RuleEngineService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The raw features of one transaction, as the service sees them at scoring time. They are stored
 * with the analysis (fraud_analyses.features) and the model is trained on exactly these stored
 * values, so training and serving cannot drift apart. Turning them into the model's numeric
 * vector is {@link FeatureSpec}'s job, defined by the model artifact.
 * <p>
 * Changing a name or meaning here invalidates every trained model: add new features instead.
 */
public final class MlFeatures {

    private MlFeatures() {
    }

    public static Map<String, Object> of(RuleContext ctx, RuleEngineService.Evaluation evaluation) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("amount", ctx.getAmount().doubleValue());
        f.put("currency", ctx.getCurrency());
        f.put("country", ctx.getCountry());
        f.put("merchant_category", ctx.getMerchantCategory());
        f.put("payment_method", ctx.getPaymentMethod());
        f.put("has_device", ctx.getDeviceId() == null || ctx.getDeviceId().isBlank() ? 0.0 : 1.0);

        int failed = 0;
        for (RuleResult r : evaluation.results()) {
            // 1 fired, 0 did not; a failed or disabled rule counts as 0 and is visible in rules_failed.
            f.put("rule_" + r.ruleCode(), r.triggered() ? 1.0 : 0.0);
            if (r.failed()) failed++;
            r.signals().forEach(f::put);
        }
        f.put("rules_failed", (double) failed);

        // Signals of rules that returned early or failed: 0 is their natural "nothing seen" value.
        f.putIfAbsent(HighAmountRule.SIGNAL_AMOUNT_TO_THRESHOLD, 0.0);
        f.putIfAbsent(VelocityRule.SIGNAL_TX_IN_WINDOW, 0.0);
        f.putIfAbsent(StructuringRule.SIGNAL_SMALL_PAYMENTS_IN_WINDOW, 0.0);
        return f;
    }
}
