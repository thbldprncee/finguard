package com.finguard.fraud.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled   score transactions with the ML model when one is available
 * @param modelDir  directory with model.json and feature_spec.json (Spring resource location);
 *                  without a model the service runs on rules only
 * @param threshold fraud probability from which the ML_SCORE rule triggers (when enabled in fraud_rules)
 */
@ConfigurationProperties("finguard.fraud.ml")
public record MlProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("classpath:ml/") String modelDir,
        @DefaultValue("0.8") double threshold
) {
}
