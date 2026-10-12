package com.finguard.fraud.service;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.entity.FraudRule;
import com.finguard.fraud.repository.FraudRuleRepository;
import com.finguard.fraud.rule.FraudRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fraud_rules table is the source of truth for enabled/weight. Settings are cached and
 * reloaded every finguard.fraud.rule-config-refresh-interval (and right after an update),
 * so a change made through the API takes effect on all instances without a restart.
 */
@Slf4j
@Service
public class RuleConfigService implements SmartInitializingSingleton {

    public record RuleSetting(boolean enabled, int weight) {}

    private final FraudRuleRepository repository;
    private final List<FraudRuleEvaluator> evaluators;
    private final FraudProperties properties;

    private volatile Map<String, RuleSetting> settings = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public RuleConfigService(FraudRuleRepository repository, List<FraudRuleEvaluator> evaluators, FraudProperties properties) {
        this.repository = repository;
        this.evaluators = evaluators;
        this.properties = properties;
    }

    /** Fail fast: an evaluator without a fraud_rules row would otherwise be silently ignored. */
    @Override
    public void afterSingletonsInstantiated() {
        reload();
        Set<String> missing = evaluators.stream().map(FraudRuleEvaluator::getRuleCode)
                .filter(code -> !settings.containsKey(code))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No fraud_rules row for rule(s) " + missing + "; add a Flyway migration");
        }
        Set<String> known = evaluators.stream().map(FraudRuleEvaluator::getRuleCode).collect(Collectors.toSet());
        settings.keySet().stream().filter(code -> !known.contains(code))
                .forEach(code -> log.warn("fraud_rules row {} has no evaluator and is ignored", code));
    }

    public Map<String, RuleSetting> current() {
        if (Instant.now().isAfter(loadedAt.plus(properties.ruleConfigRefreshInterval()))) {
            try {
                reload();
            } catch (RuntimeException e) {
                // Keep the last known configuration rather than failing every analysis.
                log.error("Could not reload fraud rule settings, using cached ones: {}", e.getMessage());
            }
        }
        return settings;
    }

    public synchronized void reload() {
        settings = repository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(FraudRule::getCode, r -> new RuleSetting(r.isEnabled(), r.getWeight())));
        loadedAt = Instant.now();
    }
}
