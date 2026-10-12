package com.finguard.transaction.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "finguard.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulingConfig implements SchedulingConfigurer {

    private final OutboxRelay relay;
    private final OutboxProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(relay::publishPending, properties.pollInterval());
        registrar.addFixedDelayTask(relay::cleanupPublished, properties.cleanupInterval());
    }
}
