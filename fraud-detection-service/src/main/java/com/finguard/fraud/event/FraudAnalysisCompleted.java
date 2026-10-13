package com.finguard.fraud.event;

import com.finguard.fraud.entity.FraudAnalysis;

/** Domain event; turned into an outbox row by {@link com.finguard.fraud.service.FraudEvents}. */
public record FraudAnalysisCompleted(FraudAnalysis analysis, String correlationId) {}
