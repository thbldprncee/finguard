package com.finguard.fraud.event;

import com.finguard.fraud.entity.FraudRule;

import java.util.UUID;

/** Domain event: audited via the outbox and triggers a reload of the rule settings after commit. */
public record FraudRuleUpdated(FraudRule rule, boolean oldEnabled, int oldWeight, UUID actorId, String correlationId) {}
