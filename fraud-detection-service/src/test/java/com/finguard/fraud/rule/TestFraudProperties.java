package com.finguard.fraud.rule;

import com.finguard.fraud.config.FraudProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

/** Binds FraudProperties like Spring does, so @DefaultValue defaults apply. */
public final class TestFraudProperties {

    private TestFraudProperties() {}

    public static FraudProperties defaults() {
        return with(Map.of());
    }

    public static FraudProperties with(Map<String, String> overrides) {
        return new Binder(new MapConfigurationPropertySource(overrides))
                .bindOrCreate("finguard.fraud", FraudProperties.class);
    }
}
